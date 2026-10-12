package id.alfindotech.kasir.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActivationDialog(
    deviceId: String,
    onActivate: (String) -> Unit
) {
    var activationCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,

        title = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(55.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "APLIKASI KASIR ANDA",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },

        text = {
            Column(Modifier.fillMaxWidth()) {

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier.padding(15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Aktivasi diperlukan untuk penggunaan APLIKASI",
                            color = Color.Red,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kirimkan DEVICE ID ke nomor 0812 9338 5557 untuk mendapatkan kode aktifasi.",
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Device ID:", fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard =
                                context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager

                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Device ID",
                                    deviceId
                                )
                            )

                            Toast.makeText(
                                context,
                                "Device ID disalin",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, null)
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))

                Text("Kode Aktivasi:", fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = activationCode,
                    onValueChange = {
                        activationCode = it.uppercase()
                    },
                    placeholder = {
                        Text("KASIR-XXXX-XXXX-XXXX")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    if (activationCode.isBlank()) {
                        Toast.makeText(
                            context,
                            "Kode aktivasi belum diisi",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onActivate(activationCode.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                Icon(Icons.Default.Key, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "AKTIFKAN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },

        dismissButton = null
    )
}
