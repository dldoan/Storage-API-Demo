package com.github.dldoan.storageapidemo


import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.api.load
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    val REQUEST_IMAGE_CAPTURE = 1
    val REQUEST_CODE_PICK_IMAGES = 2
    val REQUEST_CODE_PICK_PDFS = 3

    private var photoFile: File = File("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadImages.setOnClickListener {
            readImages()
        }

        takePhoto.setOnClickListener {
            takePhoto()
        }

        openImageSelection.setOnClickListener {
            openFilePickerImages()
        }

        openPDFSelection.setOnClickListener {
            openFilePickerPdf()
        }

        this.externalMediaDirs
    }

    /**
     *
     */
    private fun readImages() {
        val imageList = mutableListOf<Image>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )

        // Show only videos that are at least 5 minutes in duration.
        //val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES).toString()
        )

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

        val query = applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, //selection,
            null, //selectionArgs,
            sortOrder
        )
        query?.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            //val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                //val duration = cursor.getInt(durationColumn)
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                val image = Image(contentUri, name, size)
                imageList += image

                Log.wtf("Debug", "new image: $image")
            }
        }

        readImageContent.text = "size ${imageList.size} | content: $imageList"
        Log.wtf("Debug", "content: $imageList")

    }

    // Container for information about each video.
    data class Image(
        val uri: Uri,
        val name: String,
        //val duration: Int,
        val size: Int
    )


    /**
     * Create a new file to external storage which is private to your app
     * No permission required
     */
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    private fun takePhoto() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                photoFile = createImageFile()
                Log.wtf("debug", "file path: ${photoFile.path}")

                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.github.dldoan.storageapidemo.fileprovider",
                    photoFile
                )
                Log.wtf("debug", "Photo uri created: $photoURI")
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.wtf("debug", "image: ${photoFile.exists()} ${photoFile.path}")

            imageView.load(photoFile)
            copyImageToGallery(photoFile)
        }

        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == RESULT_OK) {
            Log.wtf("debug", "uri: ${data?.data}")
            Log.wtf("debug", "clipdata ${data?.clipData}")
            data?.data?.let {
                imageView2.load(it)
            }

            val clipData: ClipData? = data?.clipData
            clipData?.let {
                for (i in 0 until clipData.itemCount) {
                    val path = clipData.getItemAt(i)
                    Log.wtf("Path:", "Path: $path")

                    imageView2.load(path.uri)
                }
            }
        }

        if (requestCode == REQUEST_CODE_PICK_PDFS && resultCode == RESULT_OK) {
            Log.wtf("debug", "uri: ${data?.data}")
            Log.wtf("debug", "clipdata ${data?.clipData}")

            val clipData: ClipData? = data?.clipData
            clipData?.let {
                for (i in 0 until clipData.itemCount) {
                    val path = clipData.getItemAt(i)
                    Log.wtf("Path:", "Path: $path")
                }
            }


        }
    }

    /**
     * copy photo from external private storage to external shared storage
     * no permission required
     */
    private fun copyImageToGallery(file: File) {
        val resolver = this.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            //put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            //put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        Log.wtf("debug", "public image uri: $uri")
        uri?.let {
            resolver.openOutputStream(uri)?.use {
                it.write(file.readBytes())
            }
            imageView4.load(uri)
        }
    }


    private fun openFilePickerImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    TYPE_JPEG, TYPE_GIF, TYPE_BMP, TYPE_PNG
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // Optionally, specify a URI for the file that should appear in the
            // system file picker when it loads.
            //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGES)
    }

    private fun openFilePickerPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = TYPE_PDF
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // Optionally, specify a URI for the file that should appear in the
            // system file picker when it loads.
            //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        startActivityForResult(intent, REQUEST_CODE_PICK_PDFS)
    }

}

const val TYPE_JPEG = "image/jpeg"
const val TYPE_GIF = "image/gif"
const val TYPE_BMP = "image/bmp"
const val TYPE_PNG = "image/png"
const val TYPE_PDF = "application/pdf"
const val TYPE_MP4 = "video/mp4"
const val TYPE_ALL = "*/*"