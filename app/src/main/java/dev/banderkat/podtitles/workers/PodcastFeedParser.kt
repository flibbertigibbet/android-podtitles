package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import android.util.Xml
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.createInstance
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

data class TestMe (val one: String, val two: Int)


/**
 * Parse the RSS of a podcast feed. Looks for fields defined by iTunes:
 * https://help.apple.com/itc/podcasts_connect/#/itcb54353390
 *
 * Also looks for some additional fields in the RSS 2.0 specification:
 * https://cyber.harvard.edu/rss/rss.html
 */
class PodcastFeedParser {
    companion object {
        const val TAG = "FeedParser"
    }

    private val parser = Xml.newPullParser()

    fun parseFeed(context: Context) {
        val reader = context.assets.open("example_feed.xml")
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader.reader())
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, "rss")

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "channel") {
                readChannel()
            } else {
                skip()
            }
        }


        reader.close()
    }

    // Map of PodFeed DB field names to values read FIXME
    private val channelMap = mutableMapOf<String, Any>("url" to "testing")

    // Read the top-level channel info
    private fun readChannel() {

        // map of RSS field names to data class field names
        // where the data class field name is null, do special processing
        // fields not listed here are ignored
        val channelFields = mapOf(
            // required by RSS 2.0
            "title" to "title",
            "description" to "description",
            // required by iTunes
            "itunes:image" to null, // also RSS has a complex 'image' element; iTunes is href attr
            "language" to "language", // allowed values: https://cyber.harvard.edu/rss/languages.html
            // FIXME: "itunes:category" to null, // can be many or have subcategories
            // only want first category; in text attr
            // RSS also has a 'category' element

            // optional
            "itunes:author" to "author",
            "link" to "link", // required by RSS 2.0, but not by iTunes?
            "itunes:new-feed-url" to "url", // used for moved feeds
            "copyright" to "copyright",
            "itunes:complete" to "complete", // Yes if no more episodes will be published to this feed

            // non-iTunes optional RSS fields
            // image, which contains a title, link (to site), and url (to the image)
            "image" to null,
            "ttl" to null, // integer; number of minutes this feed may be cached
            "pubDate" to "pubDate" // last published date in RFC 822 format,
            // i.e., Sat, 07 Sep 2002 09:42:31 GMT
        )

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            val parserName = parser.name
            if (parserName in channelFields.keys) {
                val dbFieldName = channelFields[parserName]
                if (dbFieldName != null) {
                    // simple string mapping
                    channelMap[dbFieldName] = readString(parser.name)
                } else {
                    // special processing
                    when (parser.name) {
                        // FIXME: "itunes:category" -> readCategory()
                        "image" -> readImage()
                        "itunes:image" -> readItunesImage()
                        "ttl" -> channelMap["ttl"] = readInteger(parser.name)
                        else -> Log.w(TAG, "Do not know how to parse ${parser.name}")
                    }
                }
            } else {
                if (parserName == "item") readItem() else skip()
            }
        }

        Log.d(TAG, "read channel:")
        channelMap.forEach { (key, value) ->
            Log.d(TAG, "$key : $value")
        }

        Log.d(TAG, "creating pod feed object...")
        val pod = PodFeed::class.createInstance(channelMap)
        Log.d(TAG, "Created pod $pod")

    }

    // Read a single episode
    private fun readItem() {
        // map of RSS field names to data class field names
        // where the data class field name is null, do special processing
        // fields not listed here are ignored
        val itemFields = mapOf(
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
            "itunes:episode" to null, // integer, for serials; to be grouped by season
            "itunes:season" to null, // integer, for serials
            "itunes:episodeType" to "episodeType", // Full, Trailer, or Bonus, for serials
        )

        // Map of PodEpisode DB field names to values read
        // TODO: get FK ID first?
        val itemMap = mutableMapOf<String, Any>("id" to 0L, "feedId" to 0L)

        @Suppress("SwallowedException")
        @Throws(IOException::class, XmlPullParserException::class)
        fun readEnclosure() {
            parser.require(XmlPullParser.START_TAG, null, "enclosure")
            itemMap["url"] = parser.getAttributeValue(null, "url")
            itemMap["mediaType"] = parser.getAttributeValue(null, "type")
            itemMap["size"] = try {
                parser.getAttributeValue(null, "length").toInt()
            } catch (ex: ClassCastException) {
                Log.w(TAG, "Failed to read enclosure length as integer")
                0
            }
            parser.nextTag()
            parser.require(XmlPullParser.END_TAG, null, "enclosure")
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            val parserName = parser.name
            if (parserName in itemFields.keys) {
                val dbFieldName = itemFields[parserName]
                if (dbFieldName != null) {
                    // simple string mapping
                    itemMap[dbFieldName] = readString(parser.name)
                } else {
                    // special processing
                    when (parser.name) {
                        "enclosure" -> readEnclosure()
                        "itunes:episode" -> itemMap["episode"] = readInteger(parser.name)
                        "itunes:season" -> itemMap["season"] = readInteger(parser.name)
                        else -> Log.w(TAG, "Do not know how to parse ${parser.name}")
                    }
                }
            } else {
                skip()
            }
        }

        Log.d(TAG, "read episode:")
        itemMap.forEach { (key, value) ->
            Log.d(TAG, "$key : $value")
        }

        // TODO
        Log.d(TAG, "Creating episode...")
        val item = PodEpisode::class.createInstance(itemMap)
        Log.d(TAG, "Created episode: $item")
    }

    // FIXME: read the first category and subcategory, if present
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readCategory() {
        parser.require(XmlPullParser.START_TAG, null, "itunes:category")
        if (parser.next() == XmlPullParser.TEXT) {
            if (!channelMap.containsKey("category")) channelMap["category"] = parser.text
            parser.nextTag()
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            parser.require(XmlPullParser.START_TAG, null, "itunes:category")
            if (parser.next() == XmlPullParser.TEXT) {
                if (!channelMap.containsKey("subCategory")) channelMap["subCategory"] = parser.text
                parser.nextTag()
            }
            parser.require(XmlPullParser.END_TAG, null, "itunes:category")
        }
        parser.require(XmlPullParser.END_TAG, null, "itunes:category")
    }

    // read RSS 2.0 image element
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readImage() {
        parser.require(XmlPullParser.START_TAG, null, "image")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            var str = ""
            if (parser.next() == XmlPullParser.TEXT) {
                str = parser.text
                parser.nextTag()
            }

            when (parser.name) {
                "url" -> channelMap["image"] = str
                "title" -> channelMap["imageTitle"] = str
                // ignore link
            }
            parser.require(XmlPullParser.END_TAG, null, parser.name)
        }
        parser.require(XmlPullParser.END_TAG, null, "image") }

    // read the image url from the href attribute
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readItunesImage() {
        parser.require(XmlPullParser.START_TAG, null, "itunes:image")
        channelMap["image"] = parser.getAttributeValue(null, "href")
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, null, "itunes:image")
    }

    // read and return the string text value from an element
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readString(field: String): String {
        parser.require(XmlPullParser.START_TAG, null, field)
        var str = ""
        if (parser.next() == XmlPullParser.TEXT) {
            str = parser.text
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, null, field)
        return str
    }

    @Suppress("SwallowedException")
    private fun readInteger(field: String): Int {
        return try {
            readString(field).toInt()
        } catch (ex: ClassCastException) {
            Log.w(TAG, "Failed to parse $field and cast as integer")
            0
        }
    }

    // skip an element (and its children)
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip() {
        if (parser.eventType != XmlPullParser.START_TAG) {
            error("Failed to skip XML tag")
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}