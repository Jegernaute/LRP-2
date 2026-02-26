package com.example.mylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.mylist.ui.theme.MyListTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch


// 1. ГОЛОВНИЙ КЛАС ДОДАТКУ

class MainActivity : ComponentActivity() {
    // Метод onCreate запускається першим при відкритті додатку
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Робить додаток на весь екран (без чорних смуг зверху і знизу)
        setContent {
            MyListTheme {
                // Scaffold - це базовий каркас для екрану
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen() // Викликаємо наш головний екран
                    }
                }
            }
        }
    }
}


// 2. БАЗА ДАНИХ (Room Database)

// @Entity означає, що це таблиця в базі даних
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String, // Назва товару
    val isBought: Boolean = false, // Чи куплено (за замовчуванням - ні)
    // PrimaryKey генерує унікальний ID для кожного товару автоматично
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

// @Dao (Data Access Object) - це "пульт керування" базою даних
@Dao
interface ShoppingDao{
    // Отримати всі товари (відсортовані так, щоб нові були зверху)
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    // Додати новий товар
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    // Оновити існуючий товар (змінити назву або статус)
    @Update
    fun updateItem(item: ShoppingItem)

    // Видалити товар
    @Delete
    fun deleteItem(item: ShoppingItem)
}

// Головний клас бази даних, який об'єднує таблиці та пульт керування
@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase: RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        // Цей метод гарантує, що база даних створюється лише один раз (Singleton)
        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


// 3. ЛОГІКА ДОДАТКУ (ViewModel)

// ViewModel зберігає дані під час перевертання екрану і спілкується з базою
class ShoppingListViewModel(application: Application): AndroidViewModel(application){
    private val shoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()

    // Це список товарів, який "спостерігає" візуальна частина.
    // Коли він змінюється, екран оновлюється сам.
    private val _shoppingList = mutableStateListOf<ShoppingItem>()
    val shoppingList: List<ShoppingItem>
        get() = _shoppingList

    init {
        loadShoppingList() // Завантажуємо дані при запуску
    }

    // Всі звернення до бази робляться через viewModelScope.launch(IO)
    // Це "фоновий потік", щоб не гальмувати візуальний інтерфейс додатку
    private fun loadShoppingList() {
        viewModelScope.launch(IO) {
            val items = shoppingDao.getAllItems()
            _shoppingList.clear()
            _shoppingList.addAll(items)
        }
    }

    fun addItem(name:String) {
        viewModelScope.launch(IO) {
            val newItem = ShoppingItem(name = name)
            shoppingDao.insertItem(newItem) // Зберігаємо в базу
            loadShoppingList() // Оновлюємо список на екрані
        }
    }

    fun toggleBought(index: Int){
        viewModelScope.launch(IO) {
            val item = _shoppingList[index]
            // Створюємо копію товару зі зміненим статусом (true на false або навпаки)
            val updatedItem = item.copy(isBought = !item.isBought)
            shoppingDao.updateItem(updatedItem)
            _shoppingList[index] = updatedItem
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch(IO) {
            shoppingDao.deleteItem(item)
            loadShoppingList()
        }
    }

    fun editItem(item: ShoppingItem, newName: String) {
        viewModelScope.launch(IO) {
            val updatedItem = item.copy(name = newName)
            shoppingDao.updateItem(updatedItem)
            loadShoppingList()
        }
    }
}

// Фабрика для створення ViewModel (системна вимога для роботи з базою даних)
class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


// 4. ВІЗУАЛЬНИЙ ІНТЕРФЕЙС (Jetpack Compose)

// Компонент: Картка одного товару в списку
@Composable
fun ShoppingItemCard(item: ShoppingItem,
                     onToggleBought: () -> Unit = {},
                     onDelete: () -> Unit = {},
                     onEdit: () -> Unit = {}) {
    // Row - розташовує елементи в рядок (горизонтально)
    Row (modifier = Modifier
        .fillMaxWidth() // Розтягнути на всю ширину
        .padding(8.dp)
        .background(
            MaterialTheme.colorScheme.surfaceDim,
            MaterialTheme.shapes.large
        )
        .clickable { onToggleBought() } // Натискання на всю картку міняє статус
        .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ){
        Checkbox(checked = item.isBought, onCheckedChange = { onToggleBought() })

        Text(text = item.name,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)) // weight(1f) змушує текст зайняти весь вільний простір

        // Кнопка редагування (синій олівець)
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Редагувати", tint = Color.Blue)
        }
        // Кнопка видалення (червоний кошик)
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Видалити", tint = Color.Red)
        }
    }
}

// Компонент: Поле вводу та кнопка додавання
@Composable
fun AddItemButton(addItem: (String) -> Unit = {})  {
    // remember { mutableStateOf("") } - пам'ять для поля вводу. Зберігає текст, поки користувач його друкує.
    var text by remember { mutableStateOf("")}

    // Column - розташовує елементи в стовпчик (вертикально)
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it }, // Оновлює змінну при кожному натисканні клавіші
            label = { Text("Add Item") }
        )
        Button(onClick = {
            if (text.isNotEmpty()) {
                addItem(text) // Передаємо текст у ViewModel
                text = "" // Очищаємо поле після додавання
            }
        }) {
            Text("Add")
        }
    }
}

// Компонент: Головний екран, який збирає все разом
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = viewModel(
    factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application)) ){

    // Рахуємо статистику
    val totalCount = viewModel.shoppingList.size
    val boughtCount = viewModel.shoppingList.count { it.isBought }

    // Пам'ять для спливаючого вікна редагування
    var itemToEdit by remember { mutableStateOf<ShoppingItem?>(null) }
    var editText by remember { mutableStateOf("") }

    // LazyColumn - це "розумний" список. Він малює тільки ті елементи, які зараз видно на екрані.
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)){
        // 1. Блок додавання товару
        item{
            AddItemButton { viewModel.addItem(it) }
        }

        // 2. Блок статистики
        item {
            Text(
                text = "Куплено: $boughtCount з $totalCount",
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 16.sp,
                color = Color.Gray
            )
        }

        // 3. Сам список товарів
        itemsIndexed(viewModel.shoppingList) {ix, item ->
            ShoppingItemCard(item = item,
                onToggleBought = { viewModel.toggleBought(ix) },
                onDelete = { viewModel.deleteItem(item) },
                onEdit = {
                    itemToEdit = item // Запам'ятовуємо, який товар хочемо редагувати
                    editText = item.name // Підставляємо стару назву в поле вводу
                })
        }
    }

    // 4. Спливаюче вікно (з'являється тільки якщо itemToEdit не порожній)
    itemToEdit?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToEdit = null }, // Закриваємо вікно, якщо клікнули повз нього
            title = { Text("Редагувати товар") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Нова назва") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editText.isNotBlank()) {
                        viewModel.editItem(item, editText) // Зберігаємо зміни
                        itemToEdit = null // Закриваємо віконце
                    }
                }) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                Button(onClick = { itemToEdit = null }) { // Просто закриваємо віконце без збереження
                    Text("Скасувати")
                }
            }
        )
    }
}

// Це функція для попереднього перегляду (Preview) екрану в Android Studio (справа в редакторі)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}