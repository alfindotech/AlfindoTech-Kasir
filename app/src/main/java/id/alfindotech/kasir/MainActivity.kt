package id.alfindotech.kasir

import android.os.Bundle
import android.net.Uri
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import id.alfindotech.kasir.data.*
import id.alfindotech.kasir.ui.KasirTheme
import id.alfindotech.kasir.ui.SkyBlueLight
import kotlinx.coroutines.launch
import id.alfindotech.kasir.license.ActivationDialog
import id.alfindotech.kasir.license.LicenseManager
import id.alfindotech.kasir.license.LicenseKeyGenerator

private fun formatRupiah(amount: Long): String =
    "Rp. " + java.text.NumberFormat.getIntegerInstance(java.util.Locale("id", "ID")).format(amount)

data class MenuItem(
    val title: String,
    val roles: Set<String>,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KasirTheme { KasirApp(AppDatabase.get(this)) } }
    }
}

@Composable
fun KasirApp(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var licenseChecked by remember { mutableStateOf(false) }
    var activationRequired by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf("") }
    var licenseMessage by remember { mutableStateOf("") }

    // Siapkan kode perangkat dan lisensi ketika aplikasi mulai.
    LaunchedEffect(Unit) {
        try {
            val manager = LicenseManager(context)
            deviceCode = manager.deviceCode()
            var license = db.licenseDao().getLicense()
            if (license == null) {
                db.licenseDao().saveLicense(
                    LicenseEntity(deviceId = deviceCode, installDate = System.currentTimeMillis())
                )
            } else if (license.deviceId != deviceCode) {
                // Ikat lisensi pada perangkat ini; instalasi baru memulai masa trial baru.
                db.licenseDao().saveLicense(
                    LicenseEntity(deviceId = deviceCode, installDate = System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            licenseMessage = "Lisensi belum dapat diperiksa: ${e.message ?: "kesalahan database"}"
        } finally {
            licenseChecked = true
        }
    }

    if (!licenseChecked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!loggedIn) {
        LoginScreen(db) { user ->
            currentUser = user
            // Mode aktivasi baru ditampilkan setelah login berhasil.
            scope.launch {
                try {
                    val manager = LicenseManager(context)
                    val license = db.licenseDao().getLicense()
                    // Setiap login yang berhasil wajib melewati aktivasi jika lisensi belum aktif.
                    // Masa trial tidak digunakan untuk melewati halaman aktivasi.
                    activationRequired = license?.activated != true
                    licenseMessage = ""
                    loggedIn = true
                } catch (e: Exception) {
                    licenseMessage = "Gagal memeriksa aktivasi: ${e.message ?: "kesalahan database"}"
                }
            }
        }
    } else if (activationRequired) {
        ActivationDialog(deviceId = deviceCode) { enteredKey ->
            scope.launch {
                val manager = LicenseManager(context)
                val license = db.licenseDao().getLicense()
                if (license != null && manager.validate(deviceCode, enteredKey.trim())) {
                    db.licenseDao().activate(true, enteredKey.trim())
                    activationRequired = false
                } else {
                    licenseMessage = "Kode yang Anda masukkan tidak valid. Silakan periksa dan coba lagi."
                }
            }
        }
        if (licenseMessage.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { licenseMessage = "" },
                title = { Text("Aktivasi belum berhasil") },
                text = { Text(licenseMessage) },
                confirmButton = { TextButton(onClick = { licenseMessage = "" }) { Text("OK") } }
            )
        }
    } else {
        DashboardScreen(currentUser!!, db) {
            currentUser = null
            loggedIn = false
            licenseMessage = ""
        }
    }
}

@Composable
fun LoginScreen(db: AppDatabase, onLogin: (User) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (db.userDao().login("admin", "admin123") == null) {
            db.userDao().insert(User(username = "admin", password = "admin123", nama = "Administrator", role = "Admin"))
        }
        if (db.storeDao().get() == null) db.storeDao().save(StoreSettings())
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        var loginStore by remember { mutableStateOf(StoreSettings()) }
        LaunchedEffect(Unit) {
            loginStore = db.storeDao().get() ?: StoreSettings()
        }

        Image(
            painter = painterResource(id = R.drawable.alfindotech_kasir_logo),
            contentDescription = "Logo AlfindoTech Kasir",
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(12.dp))
        Text(loginStore.namaToko, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scope.launch {
                    val user = db.userDao().login(username.trim(), password)
                    if (user != null) onLogin(user) else error = "Username atau password salah"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("LOGIN") }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Text("Copyright@AlfindoTech2026", fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(user: User, db: AppDatabase, onLogout: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var storeName by remember { mutableStateOf("TOKO ALFINDO") }

    LaunchedEffect(selected) {
        if (selected == null) {
            storeName = db.storeDao().get()?.namaToko?.trim().orEmpty()
                .ifBlank { "TOKO ALFINDO" }
        }
    }

    val allMenus = listOf(
        MenuItem("Kategori", setOf("Admin", "Staff"), iconRes = R.drawable.ic_menu_kategori),
        MenuItem("Satuan", setOf("Admin", "Staff"), iconRes = R.drawable.ic_menu_satuan),
        MenuItem("Produk", setOf("Admin", "Staff"), iconRes = R.drawable.ic_menu_produk),
        MenuItem("Pembelian", setOf("Admin", "Staff"), iconRes = R.drawable.ic_menu_pembelian),
        MenuItem("Penjualan", setOf("Admin", "Staff", "Kasir"), iconRes = R.drawable.ic_menu_penjualan),
        MenuItem("User", setOf("Admin"), iconRes = R.drawable.ic_menu_user),
        MenuItem("Pengaturan", setOf("Admin"), icon = Icons.Default.Settings),
        MenuItem("Laporan", setOf("Admin", "Staff", "Kasir"), iconRes = R.drawable.ic_menu_laporan)
    )
    val menus = allMenus.filter { user.role in it.roles }

    when (selected) {
        "Produk" -> ProductScreen(db) { selected = null }
        "Kategori" -> CategoryScreen(db) { selected = null }
        "Pembelian" -> PurchaseScreen(db, user) { selected = null }
        "Penjualan" -> SaleScreen(db, user) { selected = null }
        "Pengaturan" -> SettingsScreen(db) { selected = null }
        "User" -> UserScreen(db) { selected = null }
        "Laporan" -> ReportScreen(db) { selected = null }
        "Satuan" -> UnitScreen(db) { selected = null }
        null -> Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Text(storeName, maxLines = 1, fontWeight = FontWeight.Bold)
                            Text("Dashboard • Panel Kasir", fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f))
                        }
                    },
                    actions = {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                "LOGIN: ${user.role.uppercase()}",
                                color = Color(0xFF9DEBFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = onLogout,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Keluar", fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF071A3D),
                                Color(0xFF123C72),
                                Color(0xFF087EAD),
                                Color(0xFF101B45)
                            )
                        )
                    )
            ) {
                // Ornamen cahaya lembut untuk kesan glassmorphism/glowing.
                Box(
                    Modifier
                        .offset(x = (-70).dp, y = 30.dp)
                        .size(230.dp)
                        .blur(42.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF35D8FF).copy(alpha = 0.65f), Color.Transparent)
                            ),
                            RoundedCornerShape(200.dp)
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 65.dp, y = 25.dp)
                        .size(260.dp)
                        .blur(48.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.58f), Color.Transparent)
                            ),
                            RoundedCornerShape(220.dp)
                        )
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(menus) { menu ->
                            MenuCard(menu) { selected = menu.title }
                        }
                    }
                }
            }
        }
        else -> DetailPlaceholder(selected!!, { selected = null })
    }
}

@Composable
fun MenuCard(menu: MenuItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(145.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFD9FAFF), Color(0xFFE7E1FF))),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (menu.iconRes != null) {
                    Image(
                        painter = painterResource(id = menu.iconRes),
                        contentDescription = menu.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (menu.icon != null) {
                    Icon(
                        imageVector = menu.icon,
                        contentDescription = menu.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(menu.title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(db: AppDatabase, onBack: () -> Unit) {
    val items by db.categoryDao().observeAll().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategori") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        db.categoryDao().insert(Category(nama = name.trim()))
                        name = ""
                    }
                }
            }) { Icon(Icons.Default.Add, "Tambah") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama kategori baru") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) Text("Belum ada kategori.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { item ->
                    ListItem(
                        headlineContent = { Text(item.nama) },
                        leadingContent = { Icon(Icons.Default.Category, null) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { db.categoryDao().delete(item) } }) {
                                Icon(Icons.Default.Delete, "Hapus")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitScreen(db: AppDatabase, onBack: () -> Unit) {
    val items by db.unitDao().observeAll().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Satuan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        db.unitDao().insert(Unit(nama = name.trim()))
                        name = ""
                    }
                }
            }) { Icon(Icons.Default.Add, "Tambah") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama satuan baru") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) Text("Belum ada satuan.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { item ->
                    ListItem(
                        headlineContent = { Text(item.nama) },
                        leadingContent = { Icon(Icons.Default.Straighten, null) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { db.unitDao().delete(item) } }) {
                                Icon(Icons.Default.Delete, "Hapus")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(db: AppDatabase, onBack: () -> Unit) {
    val products by db.productDao().observeAll().collectAsState(initial = emptyList())
    val categories by db.categoryDao().observeAll().collectAsState(initial = emptyList())
    val units by db.unitDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var gambarProduk by remember { mutableStateOf<String?>(null) }
    val pilihGambarProduk = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Sebagian penyedia gambar tidak mendukung izin persisten.
            } catch (_: IllegalArgumentException) {
                // URI tetap dapat digunakan selama izin dari penyedia masih berlaku.
            }
            gambarProduk = uri.toString()
        }
    }

    val nomorProdukBerikutnya =
        (products.mapNotNull {
            it.kode.removePrefix("PRD").toIntOrNull()
        }.maxOrNull() ?: 0) + 1
    val kodeOtomatis = "PRD%06d".format(nomorProdukBerikutnya)

    var nama by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var beli by remember { mutableStateOf("") }
    var jual by remember { mutableStateOf("") }
    var stok by remember { mutableStateOf("") }
    var categoryId by remember { mutableIntStateOf(0) }
    var unitId by remember { mutableIntStateOf(0) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    val selectedCategoryName =
        categories.firstOrNull { it.id == categoryId }?.nama ?: "Pilih Kategori"
    val selectedUnitName =
        units.firstOrNull { it.id == unitId }?.nama ?: "Pilih Satuan"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Tambah Produk", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = kodeOtomatis,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kode Produk (Otomatis)") },
                singleLine = true,
                readOnly = true
            )
            Spacer(Modifier.height(7.dp))

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Barcode (opsional)") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))

            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama Produk") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = {
                    if (categories.isNotEmpty()) categoryExpanded = !categoryExpanded
                }
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Kategori") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    supportingText = {
                        if (categories.isEmpty()) {
                            Text("Belum ada kategori. Tambahkan melalui menu Kategori.")
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.nama) },
                            onClick = {
                                categoryId = category.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))

            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = {
                    if (units.isNotEmpty()) unitExpanded = !unitExpanded
                }
            ) {
                OutlinedTextField(
                    value = selectedUnitName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Satuan") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                    },
                    supportingText = {
                        if (units.isEmpty()) {
                            Text("Belum ada satuan. Tambahkan melalui menu Satuan.")
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    units.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.nama) },
                            onClick = {
                                unitId = unit.id
                                unitExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))

            OutlinedTextField(
                value = beli,
                onValueChange = { beli = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Harga Beli") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))

            OutlinedTextField(
                value = jual,
                onValueChange = { jual = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Harga Jual") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))

            OutlinedTextField(
                value = stok,
                onValueChange = { stok = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Stok Awal") },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))

            Text("Gambar Produk", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.size(104.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (gambarProduk != null) {
                        AsyncImage(
                            model = gambarProduk,
                            contentDescription = "Pratinjau gambar produk",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Belum ada gambar",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { pilihGambarProduk.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("PILIH GAMBAR")
                    }
                    if (gambarProduk != null) {
                        TextButton(onClick = { gambarProduk = null }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus gambar")
                        }
                    } else {
                        Text("Pilih foto dari galeri", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    if (nama.isNotBlank() && categoryId != 0 && unitId != 0) {
                        scope.launch {
                            db.productDao().insert(
                                Product(
                                    kode = kodeOtomatis,
                                    barcode = barcode.trim(),
                                    nama = nama.trim(),
                                    categoryId = categoryId,
                                    unitId = unitId,
                                    hargaBeli = beli.toLongOrNull() ?: 0,
                                    hargaJual = jual.toLongOrNull() ?: 0,
                                    stok = stok.toIntOrNull() ?: 0,
                                    imagePath = gambarProduk
                                )
                            )
                            nama = ""
                            barcode = ""
                            beli = ""
                            jual = ""
                            stok = ""
                            gambarProduk = null
                            categoryId = 0
                            unitId = 0
                        }
                    }
                },
                enabled = nama.isNotBlank() && categoryId != 0 && unitId != 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("SIMPAN PRODUK")
            }

            Spacer(Modifier.height(18.dp))
            Text("Daftar Produk", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))

            products.forEach { product ->
                val categoryName =
                    categories.firstOrNull { it.id == product.categoryId }?.nama ?: "-"
                val unitName =
                    units.firstOrNull { it.id == product.unitId }?.nama ?: "-"

                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!product.imagePath.isNullOrBlank()) {
                            AsyncImage(
                                model = product.imagePath,
                                contentDescription = "Gambar ${product.nama}",
                                modifier = Modifier.size(76.dp).padding(end = 8.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(76.dp).padding(end = 8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = "Tidak ada gambar")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(product.nama, fontWeight = FontWeight.Bold)
                            Text("Kategori: $categoryName • Satuan: $unitName")
                            Text("${product.kode} • Stok: ${product.stok}")
                            Text("Beli: " + formatRupiah(product.hargaBeli) + "  |  Jual: " + formatRupiah(product.hargaJual))
                        }
                        IconButton(
                            onClick = {
                                scope.launch { db.productDao().delete(product) }
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Hapus")
                        }
                    }
                }
            }
        }
    }
}


data class ReportTab(val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(db: AppDatabase, onBack: () -> Unit) {
    val sales by db.saleDao().observeAll().collectAsState(initial = emptyList())
    val purchases by db.purchaseDao().observeAll().collectAsState(initial = emptyList())
    val products by db.productDao().observeAll().collectAsState(initial = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var saleFilter by remember { mutableIntStateOf(0) } // 0=Harian, 1=Bulanan
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }

    val selectedDate = java.util.Date(selectedDateMillis)
    val calendar = java.util.Calendar.getInstance().apply { time = selectedDate }
    val selectedYear = calendar.get(java.util.Calendar.YEAR)
    val selectedMonth = calendar.get(java.util.Calendar.MONTH)
    val selectedDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    fun isSameDay(timestamp: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return c.get(java.util.Calendar.YEAR) == selectedYear &&
            c.get(java.util.Calendar.MONTH) == selectedMonth &&
            c.get(java.util.Calendar.DAY_OF_MONTH) == selectedDay
    }

    fun isSameMonth(timestamp: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return c.get(java.util.Calendar.YEAR) == selectedYear &&
            c.get(java.util.Calendar.MONTH) == selectedMonth
    }

    val filteredSales = sales.filter {
        if (saleFilter == 0) isSameDay(it.tanggal) else isSameMonth(it.tanggal)
    }
    val totalFilteredSales = filteredSales.sumOf { it.total }
    val totalAllSales = sales.sumOf { it.total }
    val totalPurchases = purchases.sumOf { it.total }
    val stockValue = products.sumOf { it.hargaBeli * it.stok.toLong() }

    val filterLabel = if (saleFilter == 0) {
        "Harian: " + java.text.SimpleDateFormat(
            "dd/MM/yyyy", java.util.Locale.US
        ).format(selectedDate)
    } else {
        "Bulan: " + java.text.SimpleDateFormat(
            "MMMM yyyy", java.util.Locale("id", "ID")
        ).format(selectedDate)
    }

    if (selectedSale != null) {
        SaleDetailScreen(db, selectedSale!!, onBack = { selectedSale = null })
        return
    }
    if (selectedPurchase != null) {
        PurchaseDetailScreen(db, selectedPurchase!!, onBack = { selectedPurchase = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Text("Ringkasan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Total Penjualan", formatRupiah(totalAllSales), Modifier.weight(1f))
                SummaryCard("Pembelian", formatRupiah(totalPurchases), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SummaryCard("Nilai Stok", formatRupiah(stockValue), Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Text("Nilai Penjualan Berdasarkan Periode", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = saleFilter == 0,
                    onClick = { saleFilter = 0 },
                    label = { Text("Harian") },
                    leadingIcon = if (saleFilter == 0) {
                        { Icon(Icons.Default.Today, null) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = saleFilter == 1,
                    onClick = { saleFilter = 1 },
                    label = { Text("Bulanan") },
                    leadingIcon = if (saleFilter == 1) {
                        { Icon(Icons.Default.CalendarMonth, null) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (saleFilter == 0) Icons.Default.Today else Icons.Default.CalendarMonth,
                    null
                )
                Spacer(Modifier.width(8.dp))
                Text(filterLabel)
            }

            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(filterLabel, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("TOTAL NILAI PENJUALAN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(formatRupiah(totalFilteredSales), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("${filteredSales.size} transaksi", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Penjualan (${filteredSales.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Pembelian (${purchases.size})") }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (tab == 0) {
                if (filteredSales.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada transaksi penjualan pada $filterLabel.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredSales) { sale ->
                            Card(
                                Modifier.fillMaxWidth().clickable { selectedSale = sale }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(sale.nomor, fontWeight = FontWeight.Bold)
                                        Text(
                                            java.text.SimpleDateFormat(
                                                "dd/MM/yyyy HH:mm", java.util.Locale.US
                                            ).format(java.util.Date(sale.tanggal)),
                                            fontSize = 12.sp
                                        )
                                        Text("Total: " + formatRupiah(sale.total), fontWeight = FontWeight.SemiBold)
                                    }
                                    Icon(Icons.Default.ChevronRight, "Detail")
                                }
                            }
                        }
                    }
                }
            } else {
                if (purchases.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada transaksi pembelian.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(purchases) { purchase ->
                            Card(
                                Modifier.fillMaxWidth().clickable { selectedPurchase = purchase }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(purchase.nomor, fontWeight = FontWeight.Bold)
                                        Text(
                                            java.text.SimpleDateFormat(
                                                "dd/MM/yyyy HH:mm", java.util.Locale.US
                                            ).format(java.util.Date(purchase.tanggal)),
                                            fontSize = 12.sp
                                        )
                                        Text("Total: " + formatRupiah(purchase.total))
                                    }
                                    Icon(Icons.Default.ChevronRight, "Detail")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(db: AppDatabase, sale: Sale, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(listOf<SaleDetail>()) }
    var products by remember { mutableStateOf(listOf<Product>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(sale.id) {
        // Room DAO berikut dipanggil lewat query helper yang ditambahkan pada tahap ini.
        details = db.saleDao().getDetails(sale.id)
        products = db.productDao().getAllNow()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(sale.nomor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        java.text.SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            java.util.Locale.US
                        ).format(java.util.Date(sale.tanggal))
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(details) { d ->
                    val product = products.firstOrNull { it.id == d.productId }
                    ListItem(
                        headlineContent = { Text(product?.nama ?: "Produk #${d.productId}") },
                        supportingContent = { Text("${d.jumlah} x " + formatRupiah(d.hargaJual)) },
                        trailingContent = { Text(formatRupiah(d.subtotal), fontWeight = FontWeight.Bold) }
                    )
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    ReceiptAmountRow("TOTAL", sale.total)
                    ReceiptAmountRow("BAYAR", sale.bayar)
                    ReceiptAmountRow("KEMBALI", sale.kembalian)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(db: AppDatabase, purchase: Purchase, onBack: () -> Unit) {
    var details by remember { mutableStateOf(listOf<PurchaseDetail>()) }
    var products by remember { mutableStateOf(listOf<Product>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(purchase.id) {
        details = db.purchaseDao().getDetails(purchase.id)
        products = db.productDao().getAllNow()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Pembelian") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(purchase.nomor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        java.text.SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            java.util.Locale.US
                        ).format(java.util.Date(purchase.tanggal))
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(details) { d ->
                    val product = products.firstOrNull { it.id == d.productId }
                    ListItem(
                        headlineContent = { Text(product?.nama ?: "Produk #${d.productId}") },
                        supportingContent = { Text("${d.jumlah} x " + formatRupiah(d.hargaBeli)) },
                        trailingContent = { Text(formatRupiah(d.subtotal), fontWeight = FontWeight.Bold) }
                    )
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    ReceiptAmountRow("TOTAL", purchase.total)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPlaceholder(title: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Modul $title akan dilanjutkan pada tahap berikutnya.")
        }
    }
}


data class PurchaseCartItem(
    val product: Product,
    val hargaBeli: Long,
    val jumlah: Int
) {
    val subtotal: Long get() = hargaBeli * jumlah
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(db: AppDatabase, user: User, onBack: () -> Unit) {
    val products by db.productDao().observeAll().collectAsState(initial = emptyList())
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var hargaBeli by remember { mutableStateOf("") }
    var jumlah by remember { mutableStateOf("1") }
    var cart by remember { mutableStateOf(listOf<PurchaseCartItem>()) }
    var showProductDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val total = cart.sumOf { it.subtotal }

    fun addToCart() {
        val product = selectedProduct ?: return
        val price = hargaBeli.toLongOrNull() ?: product.hargaBeli
        val qty = jumlah.toIntOrNull() ?: 0
        if (qty <= 0 || price < 0) return

        val existing = cart.firstOrNull { it.product.id == product.id }
        cart = if (existing == null) {
            cart + PurchaseCartItem(product, price, qty)
        } else {
            cart.map {
                if (it.product.id == product.id)
                    it.copy(hargaBeli = price, jumlah = it.jumlah + qty)
                else it
            }
        }
        selectedProduct = null
        hargaBeli = ""
        jumlah = "1"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pembelian Barang") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Text("Tambah Barang", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showProductDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(selectedProduct?.nama ?: "Pilih Produk")
            }

            selectedProduct?.let {
                Text("Stok saat ini: ${it.stok}", fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hargaBeli,
                onValueChange = { hargaBeli = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Harga Beli") },
                placeholder = { Text(selectedProduct?.hargaBeli?.toString() ?: "0") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = jumlah,
                onValueChange = { jumlah = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Jumlah") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { addToCart() },
                enabled = selectedProduct != null && (jumlah.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("TAMBAH KE PEMBELIAN")
            }

            Spacer(Modifier.height(14.dp))
            Text("Daftar Pembelian", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(cart) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.product.nama, fontWeight = FontWeight.Bold)
                                Text("${item.jumlah} x " + formatRupiah(item.hargaBeli))
                                Text("Subtotal: " + formatRupiah(item.subtotal))
                            }
                            IconButton(onClick = {
                                cart = cart.filterNot { it.product.id == item.product.id }
                            }) {
                                Icon(Icons.Default.Delete, "Hapus")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(formatRupiah(total), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (cart.isEmpty() || saving) return@Button
                    saving = true
                    message = ""
                    scope.launch {
                        try {
                            db.withTransaction {
                                val nomor = "PB-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}"
                                val purchaseId = db.purchaseDao().insertPurchase(
                                    Purchase(
                                        nomor = nomor,
                                        tanggal = System.currentTimeMillis(),
                                        userId = user.id,
                                        total = total
                                    )
                                ).toInt()

                                db.purchaseDao().insertDetails(
                                    cart.map {
                                        PurchaseDetail(
                                            purchaseId = purchaseId,
                                            productId = it.product.id,
                                            hargaBeli = it.hargaBeli,
                                            jumlah = it.jumlah,
                                            subtotal = it.subtotal
                                        )
                                    }
                                )

                                cart.forEach {
                                    db.productDao().addStock(it.product.id, it.jumlah)
                                }
                            }
                            cart = emptyList()
                            message = "Pembelian berhasil disimpan. Stok bertambah otomatis."
                        } catch (e: Exception) {
                            message = "Gagal menyimpan: ${e.message ?: "kesalahan tidak diketahui"}"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = cart.isNotEmpty() && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "MENYIMPAN..." else "SIMPAN PEMBELIAN")
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }

    if (showProductDialog) {
        AlertDialog(
            onDismissRequest = { showProductDialog = false },
            title = { Text("Pilih Produk") },
            text = {
                LazyColumn {
                    items(products) { product ->
                        ListItem(
                            modifier = Modifier.clickable {
                                selectedProduct = product
                                hargaBeli = product.hargaBeli.toString()
                                showProductDialog = false
                            },
                            headlineContent = { Text(product.nama) },
                            supportingContent = { Text("Stok: ${product.stok} • Beli: " + formatRupiah(product.hargaBeli)) }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProductDialog = false }) { Text("Tutup") }
            }
        )
    }
}


data class SaleCartItem(
    val product: Product,
    val hargaJual: Long,
    val jumlah: Int
) {
    val subtotal: Long get() = hargaJual * jumlah
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(db: AppDatabase, user: User, onBack: () -> Unit) {
    val products by db.productDao().observeAll().collectAsState(initial = emptyList())
    val categories by db.categoryDao().observeAll().collectAsState(initial = emptyList())
    val context = LocalContext.current
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var jumlah by remember { mutableStateOf("1") }
    var cart by remember { mutableStateOf(listOf<SaleCartItem>()) }
    var bayar by remember { mutableStateOf("") }
    var showProductDialog by remember { mutableStateOf(false) }
    var searchProduct by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val total = cart.sumOf { it.subtotal }
    val bayarValue = bayar.toLongOrNull() ?: 0L
    val kembalian = bayarValue - total
    var lastReceipt by remember { mutableStateOf<ReceiptData?>(null) }

    if (lastReceipt != null) {
        ReceiptScreen(lastReceipt!!, db, onBack = { lastReceipt = null })
        return
    }

    fun addProductToCart(product: Product, qty: Int) {
        if (qty <= 0) {
            message = "Jumlah produk harus lebih dari 0."
            return
        }
        if (product.stok <= 0) {
            message = "Stok ${product.nama} habis."
            return
        }

        val existing = cart.firstOrNull { it.product.id == product.id }
        val newQty = (existing?.jumlah ?: 0) + qty
        if (newQty > product.stok) {
            message = "Stok ${product.nama} tidak mencukupi. Stok tersedia: ${product.stok}"
            return
        }

        cart = if (existing == null) {
            cart + SaleCartItem(product, product.hargaJual, qty)
        } else {
            cart.map {
                if (it.product.id == product.id) it.copy(jumlah = newQty) else it
            }
        }
        message = ""
    }

    // Pemilihan dari daftar langsung masuk keranjang.
    // Pemindaian barcode tetap menggunakan tombol TAMBAH KE KERANJANG.
    fun addToCart() {
        val product = selectedProduct ?: return
        val qty = jumlah.toIntOrNull() ?: 0
        addProductToCart(product, qty)
        if (message.isBlank()) {
            selectedProduct = null
            jumlah = "1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasir / Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        },
        bottomBar = {
                        Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(formatRupiah(total), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }

                    OutlinedTextField(
                        value = bayar,
                        onValueChange = { bayar = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Uang Bayar") },
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("KEMBALIAN", fontWeight = FontWeight.Bold)
                        Text(
                            if (kembalian >= 0) formatRupiah(kembalian)
                            else "Kurang " + formatRupiah(-kembalian),
                            fontWeight = FontWeight.Bold,
                            color = if (kembalian >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            if (cart.isEmpty() || bayarValue < total || saving) return@Button
                            saving = true
                            message = ""
                            val nomor = "PJ-" + java.text.SimpleDateFormat(
                                "yyyyMMdd-HHmmss",
                                java.util.Locale.US
                            ).format(java.util.Date())

                            scope.launch {
                                try {
                                    db.withTransaction {
                                        cart.forEach {
                                            val affected = db.productDao().reduceStock(it.product.id, it.jumlah)
                                            if (affected == 0) {
                                                throw IllegalStateException(
                                                    "Stok ${it.product.nama} berubah/tidak mencukupi."
                                                )
                                            }
                                        }

                                        val saleId = db.saleDao().insertSale(
                                            Sale(
                                                nomor = nomor,
                                                tanggal = System.currentTimeMillis(),
                                                userId = user.id,
                                                total = total,
                                                bayar = bayarValue,
                                                kembalian = kembalian
                                            )
                                        ).toInt()

                                        db.saleDao().insertDetails(
                                            cart.map {
                                                SaleDetail(
                                                    saleId = saleId,
                                                    productId = it.product.id,
                                                    hargaJual = it.hargaJual,
                                                    jumlah = it.jumlah,
                                                    subtotal = it.subtotal
                                                )
                                            }
                                        )
                                    }

                                    val receipt = ReceiptData(
                                        nomor = nomor,
                                        tanggal = System.currentTimeMillis(),
                                        namaKasir = user.nama,
                                        items = cart.map {
                                            ReceiptItem(
                                                kategori = categories.firstOrNull { category ->
                                                    category.id == it.product.categoryId
                                                }?.nama ?: "Kategori belum dipilih",
                                                nama = it.product.nama,
                                                jumlah = it.jumlah,
                                                harga = it.hargaJual,
                                                subtotal = it.subtotal
                                            )
                                        },
                                        total = total,
                                        bayar = bayarValue,
                                        kembalian = kembalian
                                    )
                                    lastReceipt = receipt
                                    message = "Penjualan berhasil. Kembalian ${formatRupiah(kembalian)}"
                                    cart = emptyList()
                                    bayar = ""
                                } catch (e: Exception) {
                                    message = "Gagal menyimpan: ${e.message ?: "kesalahan tidak diketahui"}"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = cart.isNotEmpty() && bayarValue >= total && !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PointOfSale, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "MEMPROSES..." else "BAYAR & SIMPAN")
                    }

                    if (message.isNotBlank()) {
                        Text(
                            message,
                            color = if (message.startsWith("Gagal") || message.contains("tidak mencukupi"))
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Tambah Produk", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchProduct,
                onValueChange = { searchProduct = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cari nama produk / barcode") },
                placeholder = { Text("Contoh: Rinso 40g atau kode barcode") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showProductDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Cari produk")
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showProductDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text("PILIH PRODUK")
                }
                Button(
                    onClick = {
                        message = ""
                        try {
                            GmsBarcodeScanning.getClient(context).startScan()
                                .addOnSuccessListener { barcode ->
                                    val code = barcode.rawValue?.trim().orEmpty()
                                    if (code.isNotBlank()) {
                                        searchProduct = code
                                        val found = products.firstOrNull {
                                            it.barcode.trim().equals(code, ignoreCase = true)
                                        }
                                        if (found != null && found.stok > 0) {
                                            selectedProduct = found
                                            message = "Barcode dikenali: ${found.nama}"
                                        } else if (found != null) {
                                            selectedProduct = null
                                            message = "Stok ${found.nama} habis."
                                        } else {
                                            selectedProduct = null
                                            message = "Produk dengan barcode $code tidak ditemukan. Pastikan barcode sudah disimpan pada data produk."
                                        }
                                    } else {
                                        message = "Barcode tidak terbaca. Coba pindai kembali."
                                    }
                                }
                                .addOnCanceledListener { }
                                .addOnFailureListener { error ->
                                    message = "Gagal membuka pemindai barcode: ${error.localizedMessage ?: "periksa Google Play Services"}"
                                }
                        } catch (error: Exception) {
                            message = "Pemindai barcode tidak dapat dibuka: ${error.localizedMessage ?: "kesalahan tidak diketahui"}"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("SCAN BARCODE")
                }
            }

            selectedProduct?.let {
                Text(
                    "Harga: " + formatRupiah(it.hargaJual) + " • Stok: ${it.stok}",
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = jumlah,
                onValueChange = { jumlah = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Jumlah") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { addToCart() },
                enabled = selectedProduct != null && (jumlah.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("TAMBAH KE KERANJANG")
            }

            Spacer(Modifier.height(12.dp))
            Text("Keranjang", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(cart) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.product.imagePath.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.product.imagePath,
                                    contentDescription = "Gambar ${item.product.nama}",
                                    modifier = Modifier.size(64.dp).padding(end = 8.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = "Produk",
                                    modifier = Modifier.size(48.dp).padding(end = 8.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    categories.firstOrNull { it.id == item.product.categoryId }?.nama
                                        ?: "Kategori belum dipilih",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(item.product.nama)
                                Text(formatRupiah(item.hargaJual))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (item.jumlah > 1) {
                                                cart = cart.map {
                                                    if (it.product.id == item.product.id)
                                                        it.copy(jumlah = it.jumlah - 1)
                                                    else it
                                                }
                                            } else {
                                                cart = cart.filterNot { it.product.id == item.product.id }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Remove, "Kurangi jumlah")
                                    }
                                    Text(item.jumlah.toString(), fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            if (item.jumlah < item.product.stok) {
                                                cart = cart.map {
                                                    if (it.product.id == item.product.id)
                                                        it.copy(jumlah = it.jumlah + 1)
                                                    else it
                                                }
                                                message = ""
                                            } else {
                                                message = "Stok ${item.product.nama} hanya ${item.product.stok}."
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, "Tambah jumlah")
                                    }
                                }
                                Text("Subtotal: " + formatRupiah(item.subtotal))
                            }
                            IconButton(onClick = {
                                cart = cart.filterNot { it.product.id == item.product.id }
                            }) {
                                Icon(Icons.Default.Delete, "Hapus")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showProductDialog) {
        AlertDialog(
            onDismissRequest = { showProductDialog = false },
            title = { Text("Pilih Produk") },
            text = {
                LazyColumn {
                    items(products.filter { product ->
                        product.stok > 0 && (searchProduct.isBlank() ||
                            product.nama.contains(searchProduct, ignoreCase = true) ||
                            product.barcode.contains(searchProduct, ignoreCase = true) ||
                            product.kode.contains(searchProduct, ignoreCase = true))
                    }) { product ->
                        ListItem(
                            modifier = Modifier.clickable {
                                addProductToCart(product, 1)
                                if (message.isBlank()) {
                                    showProductDialog = false
                                    selectedProduct = null
                                    jumlah = "1"
                                }
                            },
                            leadingContent = {
                                if (!product.imagePath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.imagePath,
                                        contentDescription = "Gambar ${product.nama}",
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = "Gambar produk",
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                            },
                            headlineContent = { Text(product.nama, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    "Barcode: ${product.barcode.ifBlank { "-" }}\nStok: ${product.stok} • Harga: " +
                                        formatRupiah(product.hargaJual) + "\nKetuk untuk langsung memasukkan ke keranjang"
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                    if (products.none { it.stok > 0 }) {
                        item {
                            Text("Tidak ada produk dengan stok tersedia.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProductDialog = false }) { Text("Tutup") }
            }
        )
    }
}


data class ReceiptItem(
    val kategori: String,
    val nama: String,
    val jumlah: Int,
    val harga: Long,
    val subtotal: Long
)

data class ReceiptData(
    val nomor: String,
    val tanggal: Long,
    val namaKasir: String,
    val items: List<ReceiptItem>,
    val total: Long,
    val bayar: Long,
    val kembalian: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(receipt: ReceiptData, db: AppDatabase, onBack: () -> Unit) {
    var store by remember { mutableStateOf(StoreSettings()) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var printMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        store = db.storeDao().get() ?: StoreSettings()
    }

    LaunchedEffect(printMessage) {
        if (printMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(printMessage)
            printMessage = ""
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Struk Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showPrinterDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Print, "Cetak")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!store.logoPath.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(store.logoPath),
                    contentDescription = "Logo toko",
                    modifier = Modifier.size(70.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Default.Store,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(store.namaToko, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (store.alamat.isNotBlank()) Text(store.alamat, fontSize = 13.sp)
            if (store.telepon.isNotBlank()) Text(store.telepon, fontSize = 13.sp)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Column(Modifier.fillMaxWidth()) {
                Text("No      : ${receipt.nomor}")
                Text(
                    "Tanggal : " + java.text.SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        java.util.Locale.US
                    ).format(java.util.Date(receipt.tanggal))
                )
                Text("Kasir   : ${receipt.namaKasir}")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(receipt.items) { item ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.nama, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.jumlah} x " + formatRupiah(item.harga))
                            Text(formatRupiah(item.subtotal))
                        }
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            ReceiptAmountRow("TOTAL", receipt.total, bold = true)
            Spacer(Modifier.height(6.dp))
            ReceiptAmountRow("BAYAR", receipt.bayar, bold = false)
            ReceiptAmountRow("KEMBALI", receipt.kembalian, bold = false)

            Spacer(Modifier.height(14.dp))
            Text("TERIMA KASIH", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Done, null)
                Spacer(Modifier.width(8.dp))
                Text("SELESAI")
            }
        }
    }

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            receipt = receipt,
            store = store,
            onDismiss = {
                showPrinterDialog = false
            },
            onResult = { result ->
                printMessage = result
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPrinterDialog(
    receipt: ReceiptData,
    store: StoreSettings,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            result[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (allowed) devices = ThermalPrinter.getPairedDevices(context)
    }

    LaunchedEffect(Unit) {
        val needs = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED

        if (needs) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            devices = ThermalPrinter.getPairedDevices(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Printer Bluetooth") },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "Tidak ada printer yang terdeteksi. Pastikan printer thermal sudah dipasangkan melalui Pengaturan Bluetooth HP."
                )
            } else {
                LazyColumn {
                    items(devices) { device ->
                        ListItem(
                            modifier = Modifier.clickable {
                                if (!loading) {
                                    loading = true
                                    Thread {
                                        val result = ThermalPrinter.print(device, store, receipt)
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            loading = false
                                            onResult(result)
                                            if (result.startsWith("Berhasil")) onDismiss()
                                        }
                                    }.start()
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Print, null) },
                            headlineContent = { Text(device.name ?: "Printer Thermal") },
                            supportingContent = { Text(device.address ?: "") }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

object ThermalPrinter {
    private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    fun getPairedDevices(context: android.content.Context): List<BluetoothDevice> {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        return adapter.bondedDevices?.toList()?.sortedBy { it.name ?: "" } ?: emptyList()
    }

    fun print(device: BluetoothDevice, store: StoreSettings, receipt: ReceiptData): String {
        var socket: BluetoothSocket? = null
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }

            socket = device.createRfcommSocketToServiceRecord(
                java.util.UUID.fromString(SPP_UUID)
            )
            socket.connect()
            val out = socket.outputStream

            out.write(byteArrayOf(0x1B, 0x40))
            out.write("\u001B\u0061\u0001".toByteArray())
            out.write("\u001B\u0045\u0001".toByteArray())
            out.write((store.namaToko.take(32) + "\n").toByteArray(Charsets.UTF_8))
            out.write("\u001B\u0045\u0000".toByteArray())

            if (store.alamat.isNotBlank())
                out.write((store.alamat.take(32) + "\n").toByteArray(Charsets.UTF_8))
            if (store.telepon.isNotBlank())
                out.write((store.telepon.take(32) + "\n").toByteArray(Charsets.UTF_8))

            out.write("\u001B\u0061\u0000".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            out.write(("No      : ${receipt.nomor}\n").toByteArray())
            out.write(("Tanggal : " + java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm", java.util.Locale.US
            ).format(java.util.Date(receipt.tanggal)) + "\n").toByteArray())
            out.write(("Kasir   : ${receipt.namaKasir}\n").toByteArray())
            out.write("--------------------------------\n".toByteArray())

            receipt.items.forEach {
                out.write(it.nama.take(32).toByteArray(Charsets.UTF_8))
                out.write("\n".toByteArray())
                val left = "${it.jumlah} x " + formatRupiah(it.harga)
                val right = formatRupiah(it.subtotal)
                val spaces = (32 - left.length - right.length).coerceAtLeast(1)
                out.write((left + " ".repeat(spaces) + right + "\n").toByteArray())
            }

            out.write("--------------------------------\n".toByteArray())
            out.write("\u001B\u0045\u0001".toByteArray())
            out.write(row("TOTAL", receipt.total))
            out.write("\u001B\u0045\u0000".toByteArray())
            out.write("\n".toByteArray())
            out.write(row("BAYAR", receipt.bayar))
            out.write(row("KEMBALI", receipt.kembalian))
            out.write("\n\u001B\u0061\u0001".toByteArray())
            out.write("TERIMA KASIH ATAS KUNJUNGAN ANDA\n".toByteArray())
            // Tambah 3 baris feed agar ujung struk keluar cukup jauh tanpa tombol FEED.
            out.write(byteArrayOf(0x1B, 0x64, 0x03))
            out.flush()

            "Berhasil mencetak ke ${device.name ?: device.address}"
        } catch (e: Exception) {
            "Gagal mencetak: ${e.message ?: "printer tidak dapat dihubungkan"}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun row(label: String, value: Long): ByteArray {
        val right = formatRupiah(value)
        val left = label.padEnd(10)
        val spaces = (32 - left.length - right.length).coerceAtLeast(1)
        return (left + " ".repeat(spaces) + right + "\n").toByteArray()
    }
}

@Composable
fun ReceiptAmountRow(label: String, amount: Long, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(formatRupiah(amount), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(db: AppDatabase, onBack: () -> Unit) {
    var nama by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            logoPath = uri.toString()
        }
    }

    LaunchedEffect(Unit) {
        val data = db.storeDao().get() ?: StoreSettings()
        nama = data.namaToko
        alamat = data.alamat
        telepon = data.telepon
        logoPath = data.logoPath
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan Toko") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier.fillMaxWidth().height(130.dp)
                    .background(SkyBlueLight, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!logoPath.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(logoPath),
                        contentDescription = "Logo toko",
                        modifier = Modifier.size(100.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        Icons.Default.Store,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(65.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("PILIH LOGO DARI GALERI")
            }

            Spacer(Modifier.height(16.dp))
            Text("Informasi Toko", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                nama, { nama = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama Toko") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                alamat, { alamat = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Alamat") },
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                telepon, { telepon = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("No. Telepon") },
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        db.storeDao().save(
                            StoreSettings(
                                id = 1,
                                namaToko = nama.trim().ifBlank { "TOKO ALFINDO" },
                                alamat = alamat.trim(),
                                telepon = telepon.trim(),
                                logoPath = logoPath
                            )
                        )
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("SIMPAN PENGATURAN")
            }

            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("Pengaturan toko berhasil disimpan.", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(db: AppDatabase, onBack: () -> Unit) {
    val users by db.userDao().observeAll().collectAsState(initial = emptyList())
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nama by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Kasir") }
    var showRole by remember { mutableStateOf(false) }
    var editingId by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun clearForm() {
        username = ""
        password = ""
        nama = ""
        role = "Kasir"
        editingId = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manajemen User") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                if (editingId == 0) "Tambah User" else "Edit User",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                username, { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
                enabled = editingId == 0
            )
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                password, { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (editingId == 0) "Password" else "Password baru (opsional)") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                nama, { nama = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama Tampil") },
                singleLine = true
            )
            Spacer(Modifier.height(7.dp))

            ExposedDropdownMenuBox(
                expanded = showRole,
                onExpandedChange = { showRole = !showRole }
            ) {
                OutlinedTextField(
                    value = role,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showRole) }
                )
                ExposedDropdownMenu(
                    expanded = showRole,
                    onDismissRequest = { showRole = false }
                ) {
                    listOf("Admin", "Staff", "Kasir").forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                role = it
                                showRole = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (username.isBlank() || nama.isBlank() || (editingId == 0 && password.isBlank())) {
                            message = "Username, nama, dan password wajib diisi untuk user baru."
                            return@Button
                        }

                        scope.launch {
                            val newPassword = password.ifBlank {
                                users.firstOrNull { it.id == editingId }?.password ?: ""
                            }

                            if (editingId == 0) {
                                val exists = users.any { it.username.equals(username.trim(), true) }
                                if (exists) {
                                    message = "Username sudah digunakan."
                                } else {
                                    db.userDao().insert(
                                        User(
                                            username = username.trim(),
                                            password = newPassword,
                                            nama = nama.trim(),
                                            role = role
                                        )
                                    )
                                    message = "User berhasil ditambahkan."
                                    clearForm()
                                }
                            } else {
                                db.userDao().insert(
                                    User(
                                        id = editingId,
                                        username = username.trim(),
                                        password = newPassword,
                                        nama = nama.trim(),
                                        role = role
                                    )
                                )
                                message = "User berhasil diperbarui."
                                clearForm()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (editingId == 0) "SIMPAN" else "UPDATE")
                }

                if (editingId != 0) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { clearForm() }
                    ) { Text("BATAL") }
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text("Daftar User", fontSize = 19.sp, fontWeight = FontWeight.Bold)

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(users) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.nama, fontWeight = FontWeight.Bold)
                                Text("${item.username} • ${item.role}")
                            }
                            IconButton(onClick = {
                                editingId = item.id
                                username = item.username
                                password = ""
                                nama = item.nama
                                role = item.role
                                message = ""
                            }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}
