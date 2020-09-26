package com.opensource.svgaplayer.utils

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 *
 * Create by im_dsd 2020/9/26 5:19 PM
 */
object SafeCloseStreamHelper {

    fun close(stream: InputStream?) {
        try {
            stream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun close(stream: OutputStream?) {
        try {
            stream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}