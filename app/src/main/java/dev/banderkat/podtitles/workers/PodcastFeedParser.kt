package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import android.util.Xml
import com.google.common.collect.ImmutableList
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


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
        val channelFields = ImmutableList.of(
            "title",
            "description",
            "link",
            "language",
            "pubDate",
            "lastBuildDate",
            "copyright"
        )

        val channelMap = mutableMapOf<String, String>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (parser.name in channelFields) {
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