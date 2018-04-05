package com.example.aaron.vision

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.client.extensions.android.json.AndroidJsonFactory
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.vision.v1.Vision
import java.util.*
import java.nio.ByteBuffer
import android.R.raw
import android.os.AsyncTask
import android.os.Message
import com.google.api.client.util.IOUtils
import com.google.api.services.vision.v1.model.*
//import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIConversion.User
import android.util.JsonReader
import com.google.api.client.json.JsonToken
import java.io.*


class MainActivity : AppCompatActivity() {

    private var bitmap:Bitmap? = null
    private var contentURI: Uri = Uri.EMPTY
    private var vision:Vision? = null
    private var gDescription = ArrayList<String>()       //holds image description info from Google

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        go_button.setOnClickListener {
            getAnImage()
            setDescription("Got the image")
        }

        ask.setOnClickListener {
            setDescription("Time to show result ...")
            GVisionThread().execute()   //talk to Google Vision in another Thread
        }

        //restore image if there is one (use after a device rotation)
        if (savedInstanceState != null && savedInstanceState[IMAGE_KEY] != null) {
            contentURI = savedInstanceState[IMAGE_KEY] as Uri
            showImage(contentURI)
        }
        setupVisionAPI()
    }

    private fun setupVisionAPI() {
        val visionBuilder = Vision.Builder(
                NetHttpTransport(),
                AndroidJsonFactory(),
                null)
        //the following uses my assigned API-key
        visionBuilder.setVisionRequestInitializer(
                VisionRequestInitializer(API_KEY))
        vision = visionBuilder.build()
    }

    /**
     * Work with Google visionAPI and request info about the picture
     * based on: https://code.tutsplus.com/tutorials/how-to-use-the-google-cloud-vision-api-in-android-apps--cms-29009
     */
    inner class GVisionThread:AsyncTask<String, String, String>() {

        private var returnedInfo = ""
        private var facts:List<EntityAnnotation>? = null

        override fun doInBackground(vararg arg: String?):String {
            Log.i(TAG, "GVisionThread")

            val bArray:ByteArray? = bitmap?.convertToByteArray()  //convertToByteArray defined below
            val myImg: Image = Image()
            myImg.encodeContent(bArray)

            val desiredFeature = Feature()
            desiredFeature.type = "LABEL_DETECTION"

            val request = AnnotateImageRequest()
            request.image = myImg
            request.features = Arrays.asList(desiredFeature)

            val batchRequest = BatchAnnotateImagesRequest()
            batchRequest.requests = Arrays.asList(request)

            Log.i(TAG, "GVisionThread - batchRequest is built")

            //  Talk to Google and get a description of the image
            Log.i(TAG, "GVisionThread - batchResponse being created")
            val batchResponse = vision?.images()?.annotate(batchRequest)?.execute()

            //Pause here waiting for Google Vision to respond

            if (batchResponse != null) {
                Log.i(TAG, "GVisionThread - batchResponse is built")
            } else {
                Log.i(TAG, "GVisionThread - batchResponse is null ----    <--------  error")
            }

            //returns a list of entityAnnotation to facts
            Log.i(TAG, "Number returned is ${batchResponse!!.responses.size}, respose[0] is ${batchResponse.responses[0].toString()}")
            returnedInfo = "${batchResponse.responses[0]}"     //returned JSon info
            facts = batchResponse.responses[0].labelAnnotations       //list of picture labels
            return returnedInfo
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            buildDescription(facts)
        }
    }


    /**
     * Pull out each label description from the parameter
     * Build a string containing these labels and print to screen
     * @param labelList The List<EntityAnnotation>? containing the label descriptions
     */
    private fun buildDescription(labelList: List<EntityAnnotation>?) {
        val llString = labelList.toString()
        val llba = llString.toByteArray(Charsets.UTF_8)
        val bstr = ByteArrayInputStream(llba)
        readJsonStream(bstr)
        var msg = ""
        if (gDescription.isNotEmpty()) {        // != null) {
            Log.i(TAG, "GVisionThread - Building screen output")
            gDescription.forEach{
                            //labelList.forEach {
                //val desc = it.description
                Log.i(TAG, "Picture label: $it")     // $desc")
                if (msg.length > 0) {   //add new line between descriptors
                    msg += "\n"
                }
                msg += it       //"$desc\n"
            }
        } else {
            Log.i(TAG, "GVisionThread - labelList is null")
        }
        setDescription(msg)
    }

    /**
     * write passed in String to the description TextField
     * @param msg the String to write
     */
    private fun setDescription(msg:String) {
        description.text = msg
    }

    /**
     * Save the image in the Bundle so it can be recovered if image is rotated
     * @param state The Bundle
     */
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        Log.i(TAG,"onSaveInstanceState:")
        state.putParcelable(IMAGE_KEY,contentURI)
    }

    /**
     * Build an Intent to retrieve an image from the gallery
     * The image is returned in onActivityResult()
     */
    private fun getAnImage() {
        val galleryIntent = Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        startActivityForResult(galleryIntent, GALLERY)
    }

    /**
     * Called by the Android system when the image's URI is returned from the Gallery
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_CANCELED) {
            return
        }
        if (data != null) {
            contentURI = data.data
            showImage(contentURI)
        }
    }

    /**
     * Display the bitmap on the screen in the_image View
     * @param uri The Bitmap to display
     */
    private fun showImage(uri:Uri) {
        try {

            val bmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            val w1 = bmap.byteCount
            val wid = bmap.width
            val factor = wid / GOAL_SIZE    //amount to reduce picture by
            Log.i(TAG,"Need to reduce image by a factor of $factor")
            bitmap = Bitmap.createScaledBitmap(bmap,bmap.width/factor, bmap.height/factor, false)
            //bitmap = bmap
            val w2 = bitmap!!.byteCount
            Log.i(TAG,"Bytecounts are: $w1 and $w2, width = $wid")
            the_image.setImageBitmap(bitmap)        //show on screen
            Toast.makeText(this@MainActivity, "Image Saved!", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, "Retrieval of image Failed!", Toast.LENGTH_SHORT).show()
            Log.i(TAG,"******ERROR***** - Retrieval of image failed")
        }
    }


    /**
     * Convert bitmap to byte array using ByteBuffer.
     * From Thomas Ivan at:
     * https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array
     */
    private fun Bitmap.convertToByteArray(): ByteArray {
        //minimum number of bytes that can be used to store this bitmap's pixels
        val size = this.byteCount
        Log.i(TAG,"convertToByteArray(): size = $size")

        //allocate new instances which will hold bitmap
        //var bytes = ByteArray(size)
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val bytes = stream.toByteArray()
        return bytes
    }

    ////////////////////////  Decode Json /////////////////////////

    fun readJsonStream(inStream: InputStream) {
        val isr = InputStreamReader(inStream,"UTF-8")
        val reader = JsonReader(isr)
        readDescriptionArray(reader);
    }

    @Throws(IOException::class)
    fun readDescriptionArray(reader: JsonReader) {
        gDescription = ArrayList<String>()

        reader.beginArray()             //skip past Json '['
        while (reader.hasNext()) {
            gDescription.add(readMessage(reader))   //pass next object from the array to readMessage()
        }
        reader.endArray()
        Log.i(TAG,"readDescriptionArray\n${gDescription.toString()}")
    }

    /**
     * parses one line of the description info
     * @param reader The JsonReader tht has the information
     * @returns A String containing the object's name and score
     */
    @Throws(IOException::class)
    fun readMessage(reader: JsonReader): String {
        var desc: String = ""
        var mid: String = ""
        var score: Double = 0.0
        var topicality: Double = 0.0


        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "description") {
                desc = reader.nextString()
            } else if (name == "mid") {
                mid = reader.nextString()
            } else if (name == "score") {
                score = reader.nextDouble()
            } else if (name == "topicality") {
                topicality = reader.nextDouble()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return "%.2f".format(score) + "\t $desc"
    }

    companion object {
        private const val TAG = "--Vision----"
        private const val GALLERY = 7734
        private const val IMAGE_KEY = "TheImage"
        private const val GOAL_SIZE = 400       //good image width size for quick response from Google
        private const val API_KEY = //your API Key should be placed here
    }

}
