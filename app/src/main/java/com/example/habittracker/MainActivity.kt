package com.example.habittracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.habittracker.ui.theme.HabitTrackerTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getDatabase(this)
        val userDao = database.userDao()
        val habitDao = database.habitDao()

        setContent {
            HabitTrackerTheme {
                val navController = rememberNavController()
                var currentUser by remember { mutableStateOf<User?>(null) }
                val scope = rememberCoroutineScope()
                
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = { user -> 
                                currentUser = user
                                navController.navigate("main") { 
                                    popUpTo("login") { inclusive = true } 
                                } 
                            },
                            onNavigateToSignup = { navController.navigate("signup") },
                            userDao = userDao
                        )
                    }
                    composable("signup") {
                        SignupScreen(
                            onSignupSuccess = { name, email, password -> 
                                scope.launch {
                                    val newUser = User(email, name, password)
                                    userDao.insertUser(newUser)
                                    currentUser = newUser
                                    navController.navigate("main") { 
                                        popUpTo("signup") { inclusive = true } 
                                    }
                                }
                            },
                            onNavigateToLogin = { navController.navigate("login") }
                        )
                    }
                    composable("main") {
                        HabitTrackerApp(
                            user = currentUser ?: User("unknown@example.com", "Unknown", ""),
                            habitDao = habitDao,
                            onLogout = {
                                currentUser = null
                                navController.navigate("login") { 
                                    popUpTo("main") { inclusive = true } 
                                }
                            },
                            onUpdateUser = { newUser -> 
                                currentUser = newUser
                                scope.launch { userDao.insertUser(newUser) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTrackerApp(user: User, habitDao: HabitDao, onLogout: () -> Unit, onUpdateUser: (User) -> Unit) {
    val context = LocalContext.current
    val habits by habitDao.getAllHabits().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("home") }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onHabitAdded = { name, desc, start, end ->
                val newHabit = Habit(
                    name = name,
                    description = desc,
                    startTime = start,
                    endTime = end
                )
                scope.launch {
                    habitDao.insertHabit(newHabit)
                }
                
                start?.let {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, it.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, it.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, name)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    scheduleNotification(context, name, it)
                }
                
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Habit Tracker",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentScreen == "home",
                    onClick = { currentScreen = "home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentScreen == "history",
                    onClick = { currentScreen = "history" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Stats") },
                    label = { Text("Stats") },
                    selected = currentScreen == "stats",
                    onClick = { currentScreen = "stats" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentScreen == "profile",
                    onClick = { currentScreen = "profile" }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen == "home") {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Habit")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                "home" -> HomeScreen(habits, 
                    onUpdateHabit = { habit -> scope.launch { habitDao.updateHabit(habit) } },
                    onDeleteHabit = { habit -> scope.launch { habitDao.deleteHabit(habit) } }
                )
                "history" -> HistoryScreen(habits)
                "stats" -> StatsScreen(habits)
                "profile" -> ProfileScreen(user, onLogout, onUpdateUser)
            }
        }
    }
}

private fun scheduleNotification(context: Context, habitName: String, time: LocalTime) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, HabitReminderReceiver::class.java).apply {
        putExtra("HABIT_NAME", habitName)
    }
    
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        habitName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, time.hour)
        set(Calendar.MINUTE, time.minute)
        set(Calendar.SECOND, 0)
        
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1)
        }
    }

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
}

@Composable
fun HomeScreen(habits: List<Habit>, onUpdateHabit: (Habit) -> Unit, onDeleteHabit: (Habit) -> Unit) {
    val today = LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Today, ${today.format(DateTimeFormatter.ofPattern("MMM dd"))}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(habits, key = { it.id }) { habit ->
                HabitItem(
                    habit = habit,
                    onToggleCompletion = {
                        val newCompletedDates = if (habit.isCompletedOn(today)) {
                            habit.completedDates - today
                        } else {
                            habit.completedDates + today
                        }
                        onUpdateHabit(habit.copy(completedDates = newCompletedDates))
                    },
                    onDelete = { onDeleteHabit(habit) }
                )
            }
        }
    }
}

@Composable
fun HabitItem(habit: Habit, onToggleCompletion: () -> Unit, onDelete: () -> Unit) {
    val today = LocalDate.now()
    val isCompleted = habit.isCompletedOn(today)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) Color.Gray else Color.Unspecified
                )
                Text(
                    text = habit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                habit.startTime?.let {
                    Text(
                        text = "Time: ${it.format(DateTimeFormatter.ofPattern("hh:mm a"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                IconButton(onClick = onToggleCompletion) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = if (isCompleted) Color(0xFF4CAF50) else Color.LightGray,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(habits: List<Habit>) {
    var selectedHabit by remember { mutableStateOf<Habit?>(habits.firstOrNull()) }
    
    LaunchedEffect(habits) {
        if (selectedHabit == null || !habits.contains(selectedHabit)) {
            selectedHabit = habits.firstOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Habit History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        if (habits.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = habits.indexOf(selectedHabit).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent
            ) {
                habits.forEach { habit ->
                    Tab(
                        selected = selectedHabit == habit,
                        onClick = { selectedHabit = habit },
                        text = { Text(habit.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            selectedHabit?.let { habit ->
                val today = LocalDate.now()
                val startOfMonth = today.withDayOfMonth(1)
                val daysInMonth = today.lengthOfMonth()
                
                Text("${today.month} Progress", style = MaterialTheme.typography.titleMedium)
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..daysInMonth).toList()) { day ->
                        val date = startOfMonth.withDayOfMonth(day)
                        val isDone = habit.isCompletedOn(date)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (isDone) Color(0xFF4CAF50) else Color.LightGray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(day.toString(), color = if (isDone) Color.White else Color.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No habits added yet", color = Color.Gray)
            }
        }
    }
}

@Composable
fun StatsScreen(habits: List<Habit>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        
        if (habits.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No habits added yet", color = Color.Gray)
                }
            }
        }

        items(habits) { habit ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(habit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("Current Streak", "${habit.currentStreak} Days")
                        StatItem("Completion Rate", "${(habit.completionRate * 100).toInt()}%")
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ProfileScreen(user: User, onLogout: () -> Unit, onUpdateUser: (User) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(user.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            Text(
                text = user.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isEditing) {
            Button(
                onClick = {
                    onUpdateUser(user.copy(name = editedName))
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
            TextButton(onClick = { isEditing = false }) {
                Text("Cancel")
            }
        } else {
            Button(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out")
        }
    }
}

@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onHabitAdded: (String, String, LocalTime?, LocalTime?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    val context = LocalContext.current

    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            startTime = LocalTime.of(h, m)
                        }, hour, minute, false).show()
                    }) {
                        Text(startTime?.let { "Start: ${it.format(DateTimeFormatter.ofPattern("hh:mm a"))}" } ?: "Set Start Time")
                    }
                    
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            endTime = LocalTime.of(h, m)
                        }, hour, minute, false).show()
                    }) {
                        Text(endTime?.let { "End: ${it.format(DateTimeFormatter.ofPattern("hh:mm a"))}" } ?: "Set End Time")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onHabitAdded(name, description, startTime, endTime) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
