package dev.banderkat.podtitles.utils

import android.content.Context
import android.util.Log
import android.util.Xml
import dev.banderkat.podtitles.database.PodDatabase
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import org.xmlpull.v1.XmlPullParser

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

class PodcastFeedParser(context: Context, feedUrl: String) {
    companion object {
        const val TAG = "FeedParser"
    }

    private val database: PodDatabase
    private val context: Context
    private val feedUrl: String
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
        "link" to "link", // required by RSS 2.0, but not by iTunes?
        "itunes:new-feed-url" to "newUrl", // used for moved feeds
        "copyright" to "copyright",
        "itunes:complete" to "complete", // Yes if no more episodes will be published to this feed
        "itunes:summary" to null, // use if description is missing

        // non-iTunes optional RSS fields
        // image, which contains a title, link (to site), and url (to the image)
        "image" to null,
        "ttl" to null, // integer; number of minutes this feed may be cached
        "pubDate" to "pubDate" // last published date in RFC 822 format,
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
        "pubDate" to "pubDate",
        "description" to "description", // required by RSS 2.0, but optional in iTunes?
        "itunes:duration" to "duration", // "recommended" to be in seconds, but can be other formats
        "link" to "link", // required by RSS 2.0, but optional in iTunes?
        "category" to "category", // RSS 2.0 simple string
        "itunes:image" to "image",
        "itunes:summary" to null, // use if description is missing
        "itunes:episode" to null, // integer, for serials; to be grouped by season
        "itunes:season" to null, // integer, for serials
        "itunes:episodeType" to "episodeType", // Full, Trailer, or Bonus, for serials
    )

    init {
        this.context = context
        this.feedUrl = feedUrl
        database = getDatabase(context)
        channelMap["url"] = feedUrl
    }

    fun fetchFeed() {
        Log.d(TAG, "Fetching podcast feed from $feedUrl...")
        parseFeed()
        Log.d(TAG, "Feed fetch for podcast at $feedUrl complete. Found ${episodes.size} episodes.")
    }

    private fun parseFeed() {
        val reader = context.assets.open(feedUrl)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader.reader())
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, "rss")

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                val channel = readChannel()
                database.podDao.addFeed(channel)
                episodes.forEach { episode -> database.podDao.addEpisode(episode) }
            } else {
                parseUtils.skip()
            }
        }
        reader.close()
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

        Log.d(TAG, "read channel:")
        channelMap.forEach { (key, value) ->
            Log.d(TAG, "$key : $value")
        }

        return PodFeed::class.createInstance(channelMap)
    }

    private fun specialChannelProcessing() {
        when (parser.name) {
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
            "itunes:image" -> {
                val image = parseUtils.readItunesImage()
                // only use itunes image tag if image tag not already found
                if (!channelMap.containsKey("image")) channelMap["image"] = image
            }
            "ttl" -> channelMap["ttl"] = parseUtils.readInteger(parser.name)
            "itunes:summary" -> {
                val summary = parseUtils.readString(parser.name)
                if (!channelMap.containsKey("description")) channelMap["description"] = summary
            }
            else -> Log.w(TAG, "Do not know how to parse ${parser.name}")
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

        Log.d(TAG, "read episode:")
        itemMap.forEach { (key, value) ->
            Log.d(TAG, "$key : $value")
        }

        // if episode does not have a GUID, use its URL as the GUID
        if (!itemMap.containsKey("guid")) itemMap["guid"] = itemMap["url"].toString()
        episodes.add(PodEpisode::class.createInstance(itemMap))
        Log.d(TAG, "Added episode. Now have ${episodes.size} episodes")
    }

    // handle fields that are not to be parsed as simple strings
    private fun specialItemProcessing() {
        when (parser.name) {
            "enclosure" -> {
                val enclosure = parseUtils.readEnclosure()
                itemMap["url"] = enclosure.url
                itemMap["mediaType"] = enclosure.type
                itemMap["size"] = enclosure.size
            }
            "itunes:episode" -> itemMap["episode"] = parseUtils.readInteger(parser.name)
            "itunes:season" -> itemMap["season"] = parseUtils.readInteger(parser.name)
            "itunes:summary" -> {
                if (itemMap.containsKey("description")) return
                itemMap["description"] = parseUtils.readString(parser.name)
            }
            else -> Log.w(TAG, "Do not know how to parse ${parser.name}")
        }
    }
}
