package com.example.lab12.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.lab12.helper.MyDBHelper
import com.example.lab12.R
import com.example.lab12.tools.Method
import com.google.gson.Gson
import okhttp3.*
import java.io.*
import java.security.SignatureException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class InitActivity : BaseActivity()  {

    class rail : ArrayList<railwayItem>()
    data class railwayItem(
        val OperatorID: String, //營運業者代碼
        val StationAddress: String, //車站地址
        val StationID: String,  //車站代碼
        val StationName: StationName,   //車站位置
        val StationPosition: StationPosition,   //車站位置
        val StationUID: String, //車站唯一識別代碼
        val UpdateTime: String, //本平台資料更新時間
        val VersionID: Int  //資料版本編號
    )
    data class StationName(
        val En: String, //英文名稱
        val Zh_tw: String   //中文繁體名稱
    )
    data class StationPosition(
        val PositionLat: Double,   //位置緯度
        val PositionLon: Double    //位置經度
    )

    val APPID = "1d75f843121143c0addc39550ba48b13"
    //申請的APPKey
    val APPKey = "CiQyJxkYO_UZY2R-0dUGNIPqoII"
    val APIUrl = "http://ptx.transportdata.tw/MOTC/v2/Rail/THSR/Station?\$format=JSON"
    private lateinit var dbrw: SQLiteDatabase

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intentfilter = IntentFilter("MyMessage")
        registerReceiver(receiver, intentfilter)
        dbrw = MyDBHelper(this).writableDatabase
        //取得資料庫實體
//台鐵授權============================================================================================================
        fun getServerTime(): String {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US
            )
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))
            return dateFormat.format(calendar.time)
        }
        //取得當下的UTC時間，Java8有提供時間格式DateTimeFormatter.RFC_1123_DATE_TIME
        var xdate = getServerTime()

        val SignDate = "x-date: $xdate"

        var Signature = ""
        try {
            //取得加密簽章
            Signature = HMAC_SHA1.Signature(SignDate, APPKey)
        } catch (e1: SignatureException) {
            e1.printStackTrace()
        }
        val sAuth =
            "hmac username=\"$APPID\", algorithm=\"hmac-sha1\", headers=\"x-date\", signature=\"$Signature\""
        val req = Request.Builder()
            .header("Authorization", sAuth)
            .header("x-date", xdate)
            // .header("Accept-Encoding","gzip")
            .url(APIUrl)
            .build()

        OkHttpClient().newCall(req).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                sendBroadcast(Intent("MyMessage").putExtra("json", response.body()?.string()))
                //val data = Gson().fromJson(response.body()?.string(), rail::class.java)
            }
            override fun onFailure(call: Call, e: IOException?) {
                Log.e("查詢失敗", "$e")
            }
        })
    }

    //廣播=============================================================================================================================
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.extras?.getString("json")?.let {
                val data = Gson().fromJson(it, rail::class.java)
                var StationName: String
                var PositionLat: Double
                var PositionLon: Double
                var StationAddress: String
                var StationID: String
                for (i in 0 until data.size) {
                    try {
                        StationName = data[i].StationName.Zh_tw
                        PositionLat = data[i].StationPosition.PositionLat
                        PositionLon = data[i].StationPosition.PositionLon
                        StationAddress = data[i].StationAddress
                        StationID = data[i].StationID
                        dbrw.execSQL("INSERT INTO myTable(StationName,PositionLat,PositionLon,StationAddress,StationID) VALUES(?,?,?,?,?)"
                            , arrayOf<Any?>(StationName, PositionLat, PositionLon, StationAddress, StationID))
                    } catch (e: Exception) {
                        Method.logE("e","${e.message}")
                    }
                }
                val i = Intent(this@InitActivity, HomepageActivity::class.java)
                startActivityForResult(i, 1)
            }
        }
    }
}
