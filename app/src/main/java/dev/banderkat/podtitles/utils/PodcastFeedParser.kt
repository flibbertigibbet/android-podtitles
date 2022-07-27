package dev.banderkat.podtitles.utils

import android.content.Context
import android.util.Log
import android.util.Xml
import dev.banderkat.podtitles.database.PodDatabase
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parse the RSS of a podcast feed. Looks for fields defined by iTunes:
 * https://help.apple.com/itc/podcasts_connect/#/itcb54353390
 *
 * Also looks for some additional fields in the RSS 2.0 specification:
 * https://cyber.harvard.edu/rss/rss.html
 *
 * See also Google Podcast feed expectations (which use iTunes/RSS 2.0 tags):
 * https://support.google.com/podcast-publishers/answer/9889544
 */

class PodcastFeedParser(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val feedUrl: String,
    displayOrder: Int
) {
    companion object {
        const val TAG = "FeedParser"
    }

    private val database: PodDatabase = getDatabase(context)
    private val parser = Xml.newPullParser()
    private val parseUtils = RSSParsingUtils(parser)

    // Map of PodFeed DB field names to values read
    private val channelMap = mutableMapOf<String, Any>()

    // Map of PodEpisode DB field names to values read
    private val itemMap = mutableMapOf<String, Any>("feedId" to feedUrl)
    private val episodes = mutableListOf<PodEpisode>()

    // Map of RSS field names to data class field names.
    // Where the data class field name is null, do special processing.
    // Fields not listed here are ignored.
    private val channelFields = mapOf(
        // required by RSS 2.0
        "title" to "title",
        "description" to "description",
        // required by iTunes
        "itunes:image" to null, // also RSS has a complex 'image' element; iTunes is href attr
        "language" to "language", // allowed values: https://cyber.harvard.edu/rss/languages.html
        "itunes:category" to null, // can be many and/or have subcategories
        // only want first category; in text attr
        // RSS also has a 'category' element

        // optional
        "itunes:author" to "author",
        "link" to null, // required by RSS 2.0, but not by iTunes?
        "itunes:new-feed-url" to "newUrl", // used for moved feeds TODO: use? convert to https?f
        "copyright" to "copyright",
        "itunes:complete" to null, // Yes if no more episodes will be published to this feed
        "itunes:summary" to null, // use if description is missing

        // non-iTunes optional RSS fields
        // image, which contains a title, link (to site), and url (to the image)
        "image" to null, // convert to https
        "ttl" to null, // integer; number of minutes this feed may be cached
        "pubDate" to null // last published date in RFC 822 format,
        // i.e., Sat, 07 Sep 2002 09:42:31 GMT
    )

    // Map of RSS field names to data class field names.
    // Where the data class field name is null, do special processing.
    // Fields not listed here are ignored.
    private val itemFields = mapOf(
        // iTunes required
        "title" to "title",
        "enclosure" to null, // contains url, length (size in bytes), and (MIME) type

        // iTunes optional
        "guid" to "guid",
        "pubDate" to null,
        "description" to "description", // required by RSS 2.0, but optional in iTunes?
        "itunes:duration" to "duration", // "recommended" to be in seconds, but can be other formats
        "link" to null, // required by RSS 2.0, but optional in iTunes?
        "category" to "category", // RSS 2.0 simple string
        "itunes:image" to null, // convert to https
        "itunes:summary" to null, // use if description is missing
        "itunes:episode" to null, // integer, for serials; to be grouped by season
        "itunes:season" to null, // integer, for serials
        "itunes:episodeType" to "episodeType", // Full, Trailer, or Bonus, for serials
    )

    init {
        channelMap["url"] = feedUrl
        channelMap["displayOrder"] = displayOrder
    }

    fun fetchFeed() {
        Log.d(TAG, "Fetching podcast feed from $feedUrl...")
        val request: Request = Request.Builder().url(feedUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                parseFeed(response.body!!.byteStream())
            } else {
                error("Podcast request failed with HTTP code ${response.code}")
            }
        }
        Log.d(TAG, "Feed fetch for podcast at $feedUrl complete. Found ${episodes.size} episodes.")
    }

    private fun parseFeed(rawXml: InputStream) {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        rawXml.reader(Charsets.UTF_8).use {
            parser.setInput(it)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "rss")

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name == "channel") {
                    val channel = readChannel()
                    database.podDao.addFeed(channel)
                    // delete existing episodes before re-adding them, to remove deleted episodes
                    // on feed refresh
                    database.podDao.deleteAllEpisodesForFeed(channel.url)
                    database.podDao.addEpisodes(episodes)
                } else {
                    parseUtils.skip()
                }
            }
        }
    }

    // Read the top-level channel info
    private fun readChannel(): PodFeed {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val parserName = parser.name
            if (parserName in channelFields.keys) {
                val dbFieldName = channelFields[parserName]
                if (dbFieldName != null) {
                    // simple string mapping
                    channelMap[dbFieldName] = parseUtils.readString(parser.name)
                } else {
                    specialChannelProcessing()
                }
            } else {
                if (parserName == "item") readItem() else parseUtils.skip()
            }
        }

        return PodFeed::class.createInstance(channelMap)
    }

    private fun specialChannelProcessing() {
        when (val parserName = parser.name) {
            "pubDate" -> {
                channelMap["pubDate"] = Utils.getIsoDate(parseUtils.readString(parserName))
            }
            "itunes:category" -> {
                val category = parseUtils.readCategory()
                if (!channelMap.containsKey("category")) channelMap["category"] = category.category
                if (!channelMap.containsKey("subCategory")) channelMap["subCategory"] =
                    category.subCategory
            }
            "image" -> {
                val image = parseUtils.readImage()
                // will overwrite iTunes image url if already found
                channelMap["image"] = image.image
                channelMap["imageTitle"] = image.title
            }
            "link" -> channelMap["link"] = parseUtils.readUrl(parserName)
            "itunes:image" -> {
                val image = parseUtils.readItunesImage()
                // only use itunes image tag if image tag not already found
                if (!channelMap.containsKey("image")) channelMap["image"] = image
            }
            "ttl" -> channelMap["ttl"] = parseUtils.readInteger(parserName)
            "itunes:summary" -> {
                val summary = parseUtils.readString(parserName)
                if (!channelMap.containsKey("description")) channelMap["description"] = summary
            }
            "itunes:complete" -> {
                val completeStr = parseUtils.readString(parserName)
                channelMap["complete"] = completeStr.lowercase() == "yes"
            }
            else -> Log.w(TAG, "Do not know how to parse ${parserName}")
        }
    }

    // Read a single episode
    private fun readItem() {
        itemMap.clear()
        itemMap["feedId"] = feedUrl

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val parserName = parser.name
            if (parserName in itemFields.keys) {
                val dbFieldName = itemFields[parserName]
                if (dbFieldName != null) {
                    // simple string mapping
                    itemMap[dbFieldName] = parseUtils.readString(parser.name)
                } else {
                    specialItemProcessing()
                }
            } else {
                parseUtils.skip()
            }
        }

        // if episode does not have a GUID, use its URL as the GUID
        if (!itemMap.containsKey("guid")) itemMap["guid"] = itemMap["url"].toString()
        episodes.add(PodEpisode::class.createInstance(itemMap))
    }

    // handle fields that are not to be parsed as simple strings
    private fun specialItemProcessing() {
        when (val parserName = parser.name) {
            "pubDate" -> {
                itemMap["pubDate"] = Utils.getIsoDate(parseUtils.readString(parserName))
            }
            "enclosure" -> {
                val enclosure = parseUtils.readEnclosure()
                itemMap["url"] = enclosure.url
                itemMap["mediaType"] = enclosure.type
                itemMap["size"] = enclosure.size
            }
            "link" -> itemMap["link"] = parseUtils.readUrl(parserName)
            "itunes:image" -> itemMap["image"] = parseUtils.readUrl(parserName)
            "itunes:episode" -> itemMap["episode"] = parseUtils.readInteger(parserName)
            "itunes:season" -> itemMap["season"] = parseUtils.readInteger(parserName)
            "itunes:summary" -> {
                val summary = parseUtils.readString(parserName)
                if (!itemMap.containsKey("description")) itemMap["description"] = summary
            }
            else -> Log.w(TAG, "Do not know how to parse $parserName")
        }
    }
}
