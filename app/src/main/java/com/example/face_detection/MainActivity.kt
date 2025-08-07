package com.example.face_detection // Paket adınız

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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.face_detection.ui.theme.Face_detectionTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay

data class PhotoItem(
    val id: Int,
    val uri: Uri? = null,
    val isValid: Boolean? = null,
    val errorMessage: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Face_detectionTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PhotoGridScreen()
                }
            }
        }
    }
}

@Composable
fun PhotoGridScreen() {
    var photoList by remember { mutableStateOf((0..29).map { PhotoItem(id = it) }) }
    var selectedPhotoId by remember { mutableStateOf<Int?>(null) }
    var photoToShowDialog by remember { mutableStateOf<PhotoItem?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoComparator = compareBy<PhotoItem> { it.uri == null }.thenBy { it.id }

    // YENİ: Tekli seçim için ayrı bir launcher
    val singlePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null && selectedPhotoId != null) {
                photoList = photoList
                    .map {
                        if (it.id == selectedPhotoId) it.copy(uri = uri, isValid = null, errorMessage = null) else it
                    }
                    .sortedWith(photoComparator)
            }
        }
    )

    // YENİ: Çoklu seçim için ayrı bir launcher
    val multiplePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { selectedUris: List<Uri> ->
            if (selectedUris.isNotEmpty()) {
                val emptySlots = photoList.filter { it.uri == null }
                val slotsToFill = emptySlots.zip(selectedUris)
                val newList = photoList.toMutableList()
                slotsToFill.forEach { (slot, uri) ->
                    val index = newList.indexOfFirst { it.id == slot.id }
                    if (index != -1) {
                        newList[index] = slot.copy(uri = uri, isValid = null, errorMessage = null)
                    }
                }
                photoList = newList.sortedWith(photoComparator)
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { isSuccess: Boolean ->
            if (isSuccess && selectedPhotoId != null) {
                photoList = photoList
                    .map {
                        if (it.id == selectedPhotoId) it.copy(uri = tempCameraUri, isValid = null, errorMessage = null) else it
                    }
                    .sortedWith(photoComparator)
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photoList, key = { it.id }) { photoItem ->
                    PhotoBox(
                        photoItem = photoItem,
                        onAddClick = {
                            selectedPhotoId = photoItem.id
                            showImageSourceDialog = true
                        },
                        onOptionsClick = {
                            photoToShowDialog = photoItem
                        },
                        onInfoClick = { message ->
                            bannerMessage = message
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // YENİ: Galeriden Toplu Ekle Butonu


            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val photosToAnalyze = photoList.filter { it.uri != null }
                    photosToAnalyze.forEach { photo ->
                        analyzeFace(context, photo.uri!!) { isSuccess, message ->
                            photoList = photoList.map {
                                if (it.id == photo.id) it.copy(isValid = isSuccess, errorMessage = message) else it
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(text = "Tümünü Analiz Et")
            }
        }
        ErrorBanner(message = bannerMessage, onDismiss = { bannerMessage = null })
    }

    photoToShowDialog?.let { photoItem ->
        AlertDialog(
            onDismissRequest = { photoToShowDialog = null },
            title = { Text("Fotoğraf Seçenekleri") },
            confirmButton = {
                Button(onClick = {
                    selectedPhotoId = photoItem.id
                    singlePhotoLauncher.launch("image/*") // "Değiştir" butonu tekli seçimi kullanır
                    photoToShowDialog = null
                }) { Text("Değiştir") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        photoList = photoList
                            .map {
                                if (it.id == photoItem.id) it.copy(uri = null, isValid = null, errorMessage = null) else it
                            }
                            .sortedWith(photoComparator)
                        photoToShowDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Kaldır") }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Kaynak Seçin") },
            confirmButton = {
                Button(onClick = {
                    val uri = createImageUri(context)
                    if (uri != null) {
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    }
                    showImageSourceDialog = false
                }) { Text("Kameradan Çek") }
            },
            dismissButton = {

                Button(
                    onClick = { multiplePhotoLauncher.launch("image/*")
                            showImageSourceDialog = false},
                ) {
                    Text(text = "Galeriden Toplu Ekle")
                }
            }
        )
    }
}

// ... PhotoBox, ErrorBanner, createImageUri, ve analyzeFace fonksiyonları aynı kalıyor ...

@Composable
fun PhotoBox(
    photoItem: PhotoItem,
    onAddClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onInfoClick: (String) -> Unit
) {
    val borderColor = when (photoItem.isValid) {
        true -> Color.Green
        false -> Color.Red
        null -> Color.Gray
    }

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .background(Color.LightGray)
            .border(2.dp, borderColor)
            .clickable { if (photoItem.uri == null) onAddClick() else onOptionsClick() },
        contentAlignment = Alignment.Center
    ) {
        if (photoItem.uri == null) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Boş Kare", tint = Color.DarkGray)
        } else {
            AsyncImage(model = photoItem.uri, contentDescription = "Seçilen Fotoğraf", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

            if (photoItem.isValid == false && photoItem.errorMessage != null) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Hata Bilgisi",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Red, CircleShape)
                        .clip(CircleShape)
                        .clickable { onInfoClick(photoItem.errorMessage) }
                        .padding(2.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.ErrorBanner(message: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        LaunchedEffect(message) {
            delay(3000)
            onDismiss()
        }
        Surface(
            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = "Hata", tint = MaterialTheme.colorScheme.onError)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = message ?: "", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
            }
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
                faces.isEmpty() -> { onResult(false, "Yüz bulunamadı.") }
                faces.size > 1 -> { onResult(false, "Birden fazla kişi var.") }
                else -> {
                    val face = faces.first()
                    val eulerY = face.headEulerAngleY; val eulerX = face.headEulerAngleX; val eulerZ = face.headEulerAngleZ
                    val eulerYThreshold = 30.0f; val eulerXThreshold = 30.0f; val eulerZThreshold = 30.0f
                    if (kotlin.math.abs(eulerY) > eulerYThreshold) { onResult(false, "Yüz çok dönük."); return@addOnSuccessListener }
                    if (kotlin.math.abs(eulerX) > eulerXThreshold) { onResult(false, "Yüz eğik."); return@addOnSuccessListener }
                    if (kotlin.math.abs(eulerZ) > eulerZThreshold) { onResult(false, "Baş çok yatık."); return@addOnSuccessListener }
                    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0.0f; val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0.0f
                    val eyeOpenThreshold = 0.4f
                    if (leftEyeOpenProb > eyeOpenThreshold && rightEyeOpenProb > eyeOpenThreshold) {
                        onResult(true, "Geçerli")
                    } else {
                        onResult(false, "Gözler kapalı.")
                    }
                }
            }
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
            onResult(false, "Kritik Hata")
        }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Face_detectionTheme {
        PhotoGridScreen()
    }
}