package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import android.util.Xml
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


/**
 * Parse the RSS of a podcast feed. Looks for the fields defined by iTunes:
 * https://help.apple.com/itc/podcasts_connect/#/itcb54353390
 *
 * Also see RSS 2.0 specification:
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

    private fun readChannel() {

        // map of RSS field names to data class field names
        val channelFields = mapOf(
            // required by RSS 2.0
            "title" to "title",
            "description" to "description",
            // required by iTunes
            "itunes:image" to "image", // also RSS has a complex 'image' element; iTunes is href attr
            "language" to "language", // allowed values: https://cyber.harvard.edu/rss/languages.html
            "itunes:category" to "category", // can be many or have subcategories
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
            "image" to "imageTitle", // here we will use it to extract alt text from its title
            "ttl" to "ttl", // integer; number of minutes this feed may be cached
            "pubDate" to "pubDate" // last published date in RFC 822 format,
            // i.e., Sat, 07 Sep 2002 09:42:31 GMT
        )

        val channelMap = mutableMapOf<String, String>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (parser.name in channelFields.keys) {
                channelMap[parser.name] = readString(parser.name)
            } else {
                skip()
            }
        }

        Log.d(TAG, "read channel:")
        channelMap.forEach { (key, value) ->
            Log.d(TAG, "$key : $value")
        }
    }

    private fun readItem() {
        val itemFields = mapOf(
            // iTunes required
            "title" to "title",
            "enclosure" to "url", // also contains length (size in bytes) and (MIME) type;
            // for now ignoring anything not audio/mpeg

            // iTunes optional
            "guid" to "guid",
            "pubDate" to "pubDate",
            "description" to "description", // required by RSS 2.0, but optional in iTunes?
            "itunes:duration" to "duration", // "recommended" to be in seconds, but can be other formats
            "link" to "link", // required by RSS 2.0, but optional in iTunes?
            "itunes:image" to "image",
            "itunes:episode" to "episode", // integer, for serials; to be grouped by season
            "itunes:season" to "season", // integer, for serials
            "itunes:episodeType" to "episodeType", // Full, Trailer, or Bonus
        )
    }

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