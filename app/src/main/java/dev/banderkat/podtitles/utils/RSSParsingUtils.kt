package dev.banderkat.podtitles.utils

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

data class EpisodeEnclosure(var url: String = "", var type: String = "", var size: Int = 0)
data class RssImage(var image: String = "", var title: String = "")
data class FeedCategory(var category: String = "", var subCategory: String = "")

/**
 * Helper functions for parsing the XML in a podcast feed.
 */
internal class RSSParsingUtils(private val parser: XmlPullParser) {
    companion object {
        const val TAG = "RSSParsingUtils"
    }

    @Suppress("SwallowedException")
    @Throws(IOException::class, XmlPullParserException::class)
    fun readEnclosure(): EpisodeEnclosure {
        parser.require(XmlPullParser.START_TAG, null, "enclosure")
        val enclosure = EpisodeEnclosure()
        enclosure.url = parser.getAttributeValue(null, "url")
        enclosure.type = parser.getAttributeValue(null, "type")
        enclosure.size = try {
            parser.getAttributeValue(null, "length").toInt()
        } catch (ex: ClassCastException) {
            Log.w(TAG, "Failed to read enclosure length as integer")
            0
        }
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, null, "enclosure")
        return enclosure
    }

    // read RSS 2.0 image element
    @Throws(IOException::class, XmlPullParserException::class)
    fun readImage(): RssImage {
        val image = RssImage()
        parser.require(XmlPullParser.START_TAG, null, "image")
        var thisTag = parser.name
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "image")) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> {
                    val str = parser.text
                    when (thisTag) {
                        "url" -> image.image = str
                        "title" -> image.title = str
                        // ignore image link (should be same as feed link)
                    }
                }
                XmlPullParser.START_TAG -> thisTag = parser.name
                XmlPullParser.END_TAG -> thisTag = null
            }
            parser.next()
        }
        parser.require(XmlPullParser.END_TAG, null, "image")
        return image
    }

    // read the image url from the href attribute
    @Throws(IOException::class, XmlPullParserException::class)
    fun readItunesImage(): String {
        parser.require(XmlPullParser.START_TAG, null, "itunes:image")
        val href = parser.getAttributeValue(null, "href")
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, null, "itunes:image")
        return href
    }

    // skip an element (and its children)
    @Throws(XmlPullParserException::class, IOException::class)
    fun skip() {
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

    // read the first category and subcategory, if present, from nested category tags
    @Throws(IOException::class, XmlPullParserException::class)
    fun readCategory(): FeedCategory {
        val category = FeedCategory()
        parser.require(XmlPullParser.START_TAG, null, "itunes:category")
        var openTags = 1
        while (openTags > 0) {
            val str = parser.getAttributeValue(null, "text")
            if (str != null && openTags == 1) {
                category.category = str
            } else if (str != null && openTags > 1) {
                category.subCategory = str
            }
            parser.next()
            when (parser.eventType) {
                XmlPullParser.START_TAG -> openTags++
                XmlPullParser.END_TAG -> openTags--
            }
        }
        return category
    }

    // read and return the string text value from an element
    @Throws(IOException::class, XmlPullParserException::class)
    fun readString(field: String): String {
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
    fun readInteger(field: String): Int {
        return try {
            readString(field).toInt()
        } catch (ex: ClassCastException) {
            Log.w(TAG, "Failed to parse $field and cast it as an integer")
            0
        }
    }
}
