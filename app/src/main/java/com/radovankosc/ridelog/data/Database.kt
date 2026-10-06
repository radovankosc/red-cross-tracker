package com.radovankosc.ridelog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "customers", indices = [Index("name")])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val homeAddress: String = "",
    /** The destination of the customer's latest ride, offered again on the next one. */
    val lastDestination: String = "",
    val note: String = "",
)

@Entity(
    tableName = "rides",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("customerId"),
        Index("dateEpochDay"),
        Index(value = ["receiptYear", "receiptNumber"], unique = true),
    ],
)
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val dateEpochDay: Long,
    val fromAddress: String,
    val toAddress: String,
    val roundTrip: Boolean,
    val oneWayKm: Double,
    val totalKm: Double,
    // The rate is copied onto each ride so old receipts keep their price when the rate changes.
    val ratePerKm: Double,
    val startFee: Double,
    val price: Double,
    val receiptYear: Int,
    val receiptNumber: Int,
    val note: String = "",
)

data class RideRow(
    @Embedded val ride: Ride,
    val customerName: String,
)

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Customer>>

    @Query("SELECT * FROM customers")
    suspend fun allOnce(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): Customer?

    @Insert
    suspend fun insert(customer: Customer): Long

    @Insert
    suspend fun insertAll(customers: List<Customer>)

    @Update
    suspend fun update(customer: Customer)

    @Delete
    suspend fun delete(customer: Customer)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM rides WHERE customerId = :id")
    suspend fun rideCount(id: Long): Int
}

private const val ROW_SELECT =
    "SELECT rides.*, customers.name AS customerName FROM rides JOIN customers ON customers.id = rides.customerId"

@Dao
abstract class RideDao {
    @Query("$ROW_SELECT ORDER BY receiptYear DESC, receiptNumber DESC LIMIT 300")
    abstract fun recent(): Flow<List<RideRow>>

    @Query("$ROW_SELECT WHERE rides.id = :id")
    abstract fun observe(id: Long): Flow<RideRow?>

    @Query("$ROW_SELECT WHERE dateEpochDay BETWEEN :from AND :to ORDER BY receiptYear, receiptNumber")
    abstract fun inRange(from: Long, to: Long): Flow<List<RideRow>>

    @Query("SELECT * FROM rides WHERE customerId = :customerId ORDER BY dateEpochDay DESC, id DESC LIMIT 1")
    abstract suspend fun lastForCustomer(customerId: Long): Ride?

    @Query(
        "SELECT a FROM (SELECT toAddress AS a FROM rides UNION SELECT fromAddress FROM rides " +
            "UNION SELECT homeAddress FROM customers UNION SELECT lastDestination FROM customers) " +
            "WHERE a != '' ORDER BY a",
    )
    abstract fun knownAddresses(): Flow<List<String>>

    @Query("SELECT * FROM rides WHERE id = :id")
    abstract suspend fun get(id: Long): Ride?

    @Query("SELECT MAX(receiptNumber) FROM rides WHERE receiptYear = :year")
    abstract suspend fun maxNumber(year: Int): Int?

    @Query("SELECT * FROM rides")
    abstract suspend fun allOnce(): List<Ride>

    @Insert
    abstract suspend fun insert(ride: Ride): Long

    @Insert
    abstract suspend fun insertAll(rides: List<Ride>)

    @Update
    abstract suspend fun update(ride: Ride)

    @Delete
    abstract suspend fun delete(ride: Ride)

    @Query("DELETE FROM rides")
    abstract suspend fun deleteAll()

    /**
     * Saves the ride with the next receipt number of its year (1, 2, 3… restarting every January),
     * or [minNumber] if that is higher, for a driver who starts using the app mid-year.
     */
    @Transaction
    open suspend fun insertNumbered(ride: Ride, minNumber: Int): Ride {
        val next = maxOf((maxNumber(ride.receiptYear) ?: 0) + 1, minNumber)
        val numbered = ride.copy(receiptNumber = next)
        return numbered.copy(id = insert(numbered))
    }
}

@Database(entities = [Customer::class, Ride::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun rides(): RideDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ridelog.db").build()
    }
}
