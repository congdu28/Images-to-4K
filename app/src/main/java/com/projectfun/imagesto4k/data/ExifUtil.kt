package com.projectfun.imagesto4k.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

object ExifUtil {

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_COLOR_SPACE
    )

    /**
     * Copy camera and shooting metadata from source image to enhanced 4K output file
     */
    fun copyExif(context: Context, sourceUri: Uri, destinationFile: File) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val sourceExif = ExifInterface(inputStream)
                val destExif = ExifInterface(destinationFile.absolutePath)

                for (tag in EXIF_TAGS) {
                    val value = sourceExif.getAttribute(tag)
                    if (value != null) {
                        destExif.setAttribute(tag, value)
                    }
                }
                destExif.saveAttributes()
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get a human-readable camera summary for photographer UI
     */
    fun getExifSummary(context: Context, uri: Uri): String {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Camera"
                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)

                val details = mutableListOf<String>()
                details.add(model)
                if (!focal.isNullOrEmpty()) details.add("${focal}mm")
                if (!fNumber.isNullOrEmpty()) details.add("f/$fNumber")
                if (!exposure.isNullOrEmpty()) {
                    val expVal = exposure.toDoubleOrNull()
                    if (expVal != null && expVal < 1.0 && expVal > 0) {
                        details.add("1/${(1.0 / expVal).toInt()}s")
                    } else {
                        details.add("${exposure}s")
                    }
                }
                if (!iso.isNullOrEmpty()) details.add("ISO $iso")

                inputStream.close()
                details.joinToString(" • ")
            } else {
                "Chi tiết ảnh tiêu chuẩn"
            }
        } catch (e: Exception) {
            "Ảnh kỹ thuật số"
        }
    }
}
