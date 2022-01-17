package kg.geektech.covid19trackingapp

import android.graphics.RectF
import com.robinhood.spark.SparkAdapter
import kg.geektech.covid19trackingapp.retrofit.CovidData
import kg.geektech.covid19trackingapp.retrofit.Metric
import kg.geektech.covid19trackingapp.retrofit.TimeScale

class CovidSparkAdapter(private val dailyData: List<CovidData>) : SparkAdapter() {

    var metric = Metric.POSITIVE
    var daysAgo =TimeScale.MAX



    override fun getCount() =dailyData.size

    override fun getItem(index: Int): Any = dailyData[index]

    override fun getY(index: Int): Float {
   var chosenDayData   = dailyData[index]

        return when(metric){
            Metric.NEGATIVE-> chosenDayData.negativeIncrease.toFloat()
            Metric.POSITIVE-> chosenDayData.positiveIncrease.toFloat()
            Metric.DEATH-> chosenDayData.deathIncrease.toFloat()
        }
    }


    override fun getDataBounds(): RectF {
        val bounds = super.getDataBounds()
        if (daysAgo != TimeScale.MAX) {
            bounds.left = count - daysAgo.numDays.toFloat()
        }
        return bounds
    }

}
