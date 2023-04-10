/*
 * Created on 2020-6-18 10:49:58 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import androidx.core.util.AtomicFile
import com.google.gson.Gson
import com.liuzhenlin.common.Configs
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.common.utils.IOUtils
import com.liuzhenlin.common.utils.Utils
import com.liuzhenlin.videos.Files
import com.liuzhenlin.videos.bean.TVGroup
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * @author 刘振林
 */
class OnlineVideoListModel(context: Context)
    : BaseModel<Nothing, Array<TVGroup>?, BaseModel.Callback>(context) {

    override fun createAndStartLoader(): AsyncTask<*, *, *> {
        val loader = LoadTVsAsyncTask()
        loader.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR, mContext)
        return loader
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadTVsAsyncTask : Loader<Context>() {

        override fun doInBackground(vararg ctxs: Context): Array<TVGroup>? {
            var json: StringBuilder? = null

            val jsonDirectory = Files.getJsonsCacheDir(ctxs[0])
            val jsonFile = AtomicFile(File(jsonDirectory, "tvs.json"))

            var ioException: IOException? = null

            var conn: HttpURLConnection? = null
            var reader: BufferedReader? = null
            var writer: BufferedWriter? = null
            var jsonFileOut: FileOutputStream? = null
            try {
                val url = URL(LINK_TVS_JSON)
                conn = url.openConnection() as HttpURLConnection
//                conn.connectTimeout = TIMEOUT_CONNECTION;
//                conn.readTimeout = TIMEOUT_READ;

                reader = BufferedReader(InputStreamReader(conn.inputStream, Configs.DEFAULT_CHARSET))
                jsonFileOut = jsonFile.startWrite()
                writer = BufferedWriter(OutputStreamWriter(jsonFileOut, Configs.DEFAULT_CHARSET))
                val buffer = CharArray(1024)
                var len: Int
                while (true) {
                    if (isCancelled) return null

                    len = reader.read(buffer)
                    if (len == -1) break

                    if (json == null) {
                        json = StringBuilder(len)
                    }
                    json.append(buffer, 0, len)
                    writer.write(buffer, 0, len)
                }
                writer.flush()
                jsonFile.finishWrite(jsonFileOut)

            } catch (e: IOException) {
                json = null
                ioException = e
            } finally {
                IOUtils.closeSilently(writer)
                IOUtils.closeSilently(reader)
                conn?.disconnect()
            }

            if (!isCancelled) {
                if (ioException != null) {
                    if (jsonFileOut != null) {
                        jsonFile.failWrite(jsonFileOut)
                    }

                    val jsonString = try {
                        jsonFile.readFully().toString(Charsets.UTF_8)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                    if (jsonString?.isNotEmpty() == true) {
                        json = StringBuilder(jsonString.length).append(jsonString)
                    }
                }

                if (!isCancelled) {
                    when {
                        json != null ->
                            return Gson().fromJson(json.toString(), Array<TVGroup>::class.java)

                        ioException != null ->
                            Utils.runOnHandlerSync(Consts.getMainThreadHandler()) {
                                if (!isCancelled) {
                                    onLoadError(ioException)
                                }
                            }
                    }
                }
            }

            return null
        }
    }

    private companion object {
        const val LINK_TVS_JSON = "https://gitlab.com/lzls/Videos-Server/-/raw/master/tvs.json"
    }
}