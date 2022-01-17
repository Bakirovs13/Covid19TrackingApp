package kg.geektech.covid19trackingapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import kg.geektech.covid19trackingapp.databinding.ActivityMainBinding
import kg.geektech.covid19trackingapp.retrofit.CovidData
import kg.geektech.covid19trackingapp.retrofit.CovidService
import kg.geektech.covid19trackingapp.retrofit.Metric
import kg.geektech.covid19trackingapp.retrofit.TimeScale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {


    private lateinit var covidService: CovidService
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var  binding: ActivityMainBinding
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private  lateinit var nationalDailyData: List<CovidData>


    companion object{
        const val  ALL_STATES ="All(NationWide)"
        const val BASE_URL = "https://covidtracking.com/api/v1/"
        const val TAG ="MainActivity"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.app_description)
        initRetrofit()
        fetchNationalData()
        fetchTheStateData()

        }

    private fun initRetrofit() {
        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        covidService =retrofit.create(CovidService::class.java)
    }
    private fun fetchNationalData() {
        //fetch national data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national data")
                updateDisplayWithData(nationalDailyData)
            }
        })

    }

    private fun fetchTheStateData() {
        //fetch the state data

        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val statesData = response.body()
                if (statesData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                perStateDailyData = statesData
                    .filter { it.dateChecked != null }
                    .map { // State data may have negative deltas, which don't make sense to graph
                        CovidData(
                            it.dateChecked,
                            it.positiveIncrease.coerceAtLeast(0),
                            it.negativeIncrease.coerceAtLeast(0),
                            it.deathIncrease.coerceAtLeast(0),
                            it.state
                        ) }
                    .reversed()
                    .groupBy { it.state }
                Log.i(TAG, "Update spinner with state names")
                updateSpinnerWithStateData(perStateDailyData.keys)
            }
        })

    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, ALL_STATES)
        binding.spinnerSelect.attachDataSource(stateAbbreviationList)
        binding.spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }


}

    private fun setupEventListeners() {

        //add listener for the user scrubbing on the chart
        binding.sparkView.isScrubEnabled = true
        binding.sparkView.setScrubListener { itemData->
            if (itemData is CovidData){
                updateInfoforDate(itemData)
            }
        }
        binding.tickerView.setCharacterLists(TickerUtils.provideNumberList())

        binding.radioGroupTimeSelection.setOnCheckedChangeListener{_,checkedId->
            adapter.daysAgo = when(checkedId){
                R.id.radioButtonWeek-> TimeScale.WEEK
                R.id.radioButtonMonth-> TimeScale.MONTH
               else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        binding.radioGroupMetricSelection.setOnCheckedChangeListener{_,checkedId->
            when(checkedId){
                R.id.radioButtonPositive-> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative-> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonDeath-> updateDisplayMetric(Metric.DEATH)

            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //update the color of the chart
     val colorRes = when(metric){
            Metric.NEGATIVE-> R.color.colorNegative
            Metric.POSITIVE-> R.color.colorPositive
            Metric.DEATH-> R.color.colorDeath
        }

        @ColorInt
       val colorInt = ContextCompat.getColor(this,colorRes)
         binding.sparkView.lineColor = colorInt
        binding.tickerView.textColor =colorInt


        //update the metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()

        //Reset the number and date shown in the bottom text view
        updateInfoforDate(currentlyShownData.last())

    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
       //create spark adapter
        adapter =CovidSparkAdapter(dailyData)
        binding.sparkView.adapter =adapter
        //update radio buttons to select the positive cases and max time by default
        binding.radioButtonPositive.isChecked = true
        binding.radioButtonMax.isChecked = true
        //display metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)

    }

    private fun updateInfoforDate(covidData: CovidData) {

        val numCases = when(adapter.metric){
            Metric.DEATH-> covidData.deathIncrease
            Metric.POSITIVE-> covidData.positiveIncrease
            Metric.NEGATIVE-> covidData.negativeIncrease
        }

        //tvMetricLabel

        binding.tickerView.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat =  SimpleDateFormat("MMM dd ,yyyy", Locale.US)
       binding.tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)




    }
}
