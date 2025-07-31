package com.fourpixell.facerecognition.registerfacesection

import android.app.Application
import android.util.Log
import androidx.compose.runtime.currentComposer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer


//Data class to check if face is detected
data class FaceScanUiState(
    val isFaceDetected: Boolean = false,
    val saveResult: SaveResult? = null
)

//Enum class to save  constant values of result
enum class SaveResult {
    SUCCESS, FAILURE
}

class RegisterNewFaceViewModel(application: Application) : AndroidViewModel(application){

    private val  _uiState = MutableStateFlow(FaceScanUiState())
    val uiState: StateFlow<FaceScanUiState> = _uiState.asStateFlow()

    //Store the latest face data temporarily
    private var latestFaceData: FloatBuffer? = null

    //Storing New Data
    fun onFaceUpdated (data: FloatBuffer?){
        this.latestFaceData = data
        _uiState.update { currentState ->
            currentState.copy(isFaceDetected = data != null)
        }
    }

    //Save New Face Data
    fun saveFaceData(){
        latestFaceData?.let { data ->
            viewModelScope.launch {
                val success = saveFaceDataToFile(data)
                _uiState.update { currentState ->
                    currentState.copy(
                        saveResult = if (success) SaveResult.SUCCESS else SaveResult.FAILURE,
                        isFaceDetected = false
                    )
                }
                latestFaceData = null
            }

        }
    }

    //Reset save result state
    fun consumeSaveResult(){
        _uiState.update {
            it.copy(saveResult = null)
        }
    }

    //Saving Face data
    private fun saveFaceDataToFile(data: FloatBuffer): Boolean {

        val context = getApplication<Application>().applicationContext

        // Create a copy of the buffer's data so original one is not consumed
        val dataToSave = FloatArray(data.remaining())
        data.get(dataToSave)

        // File name.
        val filename = "face_mesh_${System.currentTimeMillis()}.txt"
        val file = File(context.filesDir, filename)

        return try {
            file.bufferedWriter().use { out ->
                // Convert the float array to a comma-separated string
                out.write(dataToSave.joinToString(","))
            }
            Log.d("FaceScanViewModel", "Successfully saved data to ${file.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e("FaceScanViewModel", "Error writing face data to file", e)
            false
        }
    }

}
