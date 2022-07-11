package dev.banderkat.podtitles

import android.app.SearchManager
import android.content.Context
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.*
import dev.banderkat.podtitles.databinding.ActivityMainBinding
import dev.banderkat.podtitles.workers.AUTHORS_RESULT_KEY
import dev.banderkat.podtitles.workers.PODCAST_QUERY_PARAM
import dev.banderkat.podtitles.workers.PodcastSearchWorker
import dev.banderkat.podtitles.workers.TITLES_RESULT_KEY
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SEARCH_WORK_TAG = "podtitles_search"
        const val SEARCH_DEBOUNCE_MS = 500L
    }

    private val workManager = WorkManager.getInstance(this)

    private lateinit var binding: ActivityMainBinding
    private lateinit var cursorAdapter: CursorAdapter
    private lateinit var searchView: SearchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        val searchItem: MenuItem? = menu.findItem(R.id.action_search_podcasts)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView = searchItem?.actionView as SearchView

        searchView.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            val from =
                arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2)
            val to = intArrayOf(R.id.search_result_title, R.id.search_results_subtitle)
            cursorAdapter = SimpleCursorAdapter(
                context, R.layout.search_result_item, null,
                from, to, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
            )
            suggestionsAdapter = cursorAdapter

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d(TAG, "query text $query submitted")
                    // TODO: hide keyboard?
                    return false
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    Log.d(TAG, "Query text changed to $query")
                    if (query.isNullOrEmpty()) return true

                    val cursor = MatrixCursor(
                        arrayOf(
                            BaseColumns._ID,
                            SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2
                        )
                    )
                    val loading = getString(R.string.loading)
                    cursor.addRow(arrayOf(0, loading, ""))

                    suggestionsAdapter.changeCursor(cursor)

                    searchPodcasts(query)
                    return true
                }
            })

            setIconifiedByDefault(false)
            setOnSuggestionListener(object : SearchView.OnSuggestionListener {
                override fun onSuggestionSelect(position: Int): Boolean {
                    Log.d(TAG, "suggestion selected at $position")
                    return false
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    Log.d(TAG, "suggestion clicked at $position")
                    return true
                }
            })
        }
        return true
    }

    private fun searchPodcasts(query: String) {
        Log.d(TAG, "Go search for podcasts with $query")

        // cancel any previous searches
        workManager.cancelAllWorkByTag(SEARCH_WORK_TAG)

        val searchRequest = OneTimeWorkRequestBuilder<PodcastSearchWorker>()
            .setInitialDelay(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS) // debounce
            .setInputData(workDataOf(PODCAST_QUERY_PARAM to query))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(SEARCH_WORK_TAG)
            .build()

        workManager.enqueue(searchRequest)
        workManager
            .getWorkInfoByIdLiveData(searchRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Successfully got search results")
                        val titles = workInfo.outputData.getStringArray(TITLES_RESULT_KEY)
                        val authors = workInfo.outputData.getStringArray(AUTHORS_RESULT_KEY)

                        val cursor = MatrixCursor(
                            arrayOf(
                                BaseColumns._ID,
                                SearchManager.SUGGEST_COLUMN_TEXT_1,
                                SearchManager.SUGGEST_COLUMN_TEXT_2
                            )
                        )
                        query.let {
                            titles?.forEachIndexed { index, suggestion ->
                                if (suggestion.contains(query, true))
                                    cursor.addRow(arrayOf(index, suggestion, authors?.get(index)))
                            }
                        }
                        cursorAdapter.changeCursor(cursor)
                        cursorAdapter.notifyDataSetChanged()
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Search worker failed")
                    }
                    else -> {
                        Log.d(TAG, "Search worker moved to state ${workInfo?.state}")
                    }
                }
            }
    }
}