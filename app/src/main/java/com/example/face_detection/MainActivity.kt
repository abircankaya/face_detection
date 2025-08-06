package com.example.face_detection

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.face_detection.ui.theme.Face_detectionTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Face_detectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FaceAnalysisScreen()
                }
            }
        }
    }
}

@Composable
fun FaceAnalysisScreen() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var resultText by remember { mutableStateOf("Bir fotoğraf seçin veya çekin.") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Kamera için geçici URI tutacak bir state
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                imageUri = it
                resultText = "Fotoğraf analiz edilmeye hazır."
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { isSuccess: Boolean ->
            if (isSuccess) {
                imageUri = tempCameraUri
                resultText = "Fotoğraf analiz edilmeye hazır."
            } else {
                resultText = "Kamera işlemi iptal edildi."
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (imageUri == null) {
            Image(
                painter = painterResource(id = android.R.drawable.ic_menu_camera),
                contentDescription = "Placeholder Fotoğraf",
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = "Seçilen Fotoğraf",
                modifier = Modifier.size(250.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
        } else {
            Spacer(modifier = Modifier.height(68.dp))
        }
        Text(
            text = resultText,
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { galleryLauncher.launch("image/*") }) {
                Text(text = "Galeriden Seç")
            }
            Button(
                onClick = {
                    val uri = createImageUri(context)
                    if (uri != null) {
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        Toast.makeText(context, "Hata: Medya deposuna kayıt oluşturulamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(text = "Fotoğraf Çek")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                imageUri?.let {
                    isLoading = true
                    analyzeFace(context, it) { _, message ->
                        resultText = message
                        isLoading = false
                    }
                }
            },
            enabled = imageUri != null && !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(text = "Yüzü Analiz Et")
        }
    }
}

private fun createImageUri(context: Context): Uri? {
    val contentResolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "face_analysis_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FaceAnalysisApp")
        }
    }
    return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}

private fun analyzeFace(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    val image: InputImage
    try {
        image = InputImage.fromFilePath(context, uri)
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(false, "Hata: Resim dosyası okunamadı.")
        return
    }

    val detector = FaceDetection.getClient(highAccuracyOpts)
    detector.process(image)
        .addOnSuccessListener { faces ->
            when {
                faces.isEmpty() -> {
                    onResult(false, "Analiz Başarısız: Fotoğrafta yüz bulunamadı.")
                }
                faces.size > 1 -> {
                    onResult(false, "Analiz Başarısız: Lütfen sadece tek bir kişinin olduğu bir fotoğraf seçin.")
                }
                else -> {
                    val face = faces.first()

                    val eulerY = face.headEulerAngleY
                    val eulerX = face.headEulerAngleX
                    val eulerZ = face.headEulerAngleZ

                    val eulerYThreshold = 30.0f
                    val eulerXThreshold = 30.0f
                    val eulerZThreshold = 30.0f

                    if (kotlin.math.abs(eulerY) > eulerYThreshold) {
                        onResult(false, "Analiz Başarısız: Yüz sağa veya sola çok dönük (yan profil).")
                        return@addOnSuccessListener
                    }
                    if (kotlin.math.abs(eulerX) > eulerXThreshold) {
                        onResult(false, "Analiz Başarısız: Yüz yukarı veya aşağı çok bakıyor.")
                        return@addOnSuccessListener
                    }
                    if (kotlin.math.abs(eulerZ) > eulerZThreshold) {
                        onResult(false, "Analiz Başarısız: Baş sağa veya sola çok yatık.")
                        return@addOnSuccessListener
                    }

                    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0.0f
                    val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0.0f
                    val eyeOpenThreshold = 0.4f

                    if (leftEyeOpenProb > eyeOpenThreshold && rightEyeOpenProb > eyeOpenThreshold) {
                        onResult(true, "Analiz Başarılı: Fotoğraf tüm koşullara uygun.")
                    } else {
                        onResult(false, "Analiz Başarısız: Fotoğraftaki yüzün gözleri yeterince açık değil.")
                    }
                }
            }
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
            onResult(false, "Hata: ${e.localizedMessage}")
        }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Face_detectionTheme {
        FaceAnalysisScreen()
    }
}