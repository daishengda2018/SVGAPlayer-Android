package com.opensource.svgaplayer.utils

import android.graphics.BitmapFactory
import android.util.Log

/**
 *
 * Create by im_dsd 2020/7/7 17:59
 */
internal object BitmapUtils {

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        Log.d("##SVGA","## calculateInSampleSize  $reqWidth $reqHeight $width $height")

        if (reqHeight <= 0 || reqWidth <= 0) {
            return inSampleSize
        }
        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}