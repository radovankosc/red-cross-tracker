package com.radovankosc.ridelog.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Everything the app stores, as one JSON file the user can keep somewhere safe and restore later. */
object Backup {

    suspend fun export(db: AppDatabase, settings: AppSettings): String {
        val customers = JSONArray()
        db.customers().allOnce().forEach { c ->
            customers.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phone", c.phone)
                    .put("homeAddress", c.homeAddress)
                    .put("lastDestination", c.lastDestination)
                    .put("note", c.note),
            )
        }
        val rides = JSONArray()
        db.rides().allOnce().forEach { r ->
            rides.put(
                JSONObject()
                    .put("id", r.id)
                    .put("customerId", r.customerId)
                    .put("dateEpochDay", r.dateEpochDay)
                    .put("fromAddress", r.fromAddress)
                    .put("toAddress", r.toAddress)
                    .put("roundTrip", r.roundTrip)
                    .put("oneWayKm", r.oneWayKm)
                    .put("totalKm", r.totalKm)
                    .put("ratePerKm", r.ratePerKm)
                    .put("startFee", r.startFee)
                    .put("price", r.price)
                    .put("receiptYear", r.receiptYear)
                    .put("receiptNumber", r.receiptNumber)
                    .put("note", r.note),
            )
        }
        val s = JSONObject()
            .put("ratePerKm", settings.ratePerKm)
            .put("startFee", settings.startFee)
            .put("driverName", settings.driverName)
            .put("organization", settings.organization)
            .put("reportEmail", settings.reportEmail)
            .put("mapsApiKey", settings.mapsApiKey)
            .put("regionCode", settings.regionCode)
            .put("numberingYear", settings.numberingYear)
            .put("numberingStart", settings.numberingStart)
        return JSONObject()
            .put("app", "ridelog")
            .put("version", 1)
            .put("settings", s)
            .put("customers", customers)
            .put("rides", rides)
            .toString(2)
    }

    /** Replaces all data with the backup's. Returns the restored settings. */
    suspend fun restore(db: AppDatabase, text: String): AppSettings {
        val root = JSONObject(text)
        require(root.optString("app") == "ridelog") { "Toto nie je záložný súbor aplikácie Kniha jázd" }
        val customers = root.getJSONArray("customers").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Customer(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone"),
                    homeAddress = o.optString("homeAddress"),
                    lastDestination = o.optString("lastDestination"),
                    note = o.optString("note"),
                )
            }
        }
        val rides = root.getJSONArray("rides").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Ride(
                    id = o.getLong("id"),
                    customerId = o.getLong("customerId"),
                    dateEpochDay = o.getLong("dateEpochDay"),
                    fromAddress = o.getString("fromAddress"),
                    toAddress = o.getString("toAddress"),
                    roundTrip = o.getBoolean("roundTrip"),
                    oneWayKm = o.getDouble("oneWayKm"),
                    totalKm = o.getDouble("totalKm"),
                    ratePerKm = o.getDouble("ratePerKm"),
                    startFee = o.getDouble("startFee"),
                    price = o.getDouble("price"),
                    receiptYear = o.getInt("receiptYear"),
                    receiptNumber = o.getInt("receiptNumber"),
                    note = o.optString("note"),
                )
            }
        }
        val s = root.optJSONObject("settings") ?: JSONObject()
        val d = AppSettings()
        val settings = AppSettings(
            ratePerKm = s.optDouble("ratePerKm", d.ratePerKm),
            startFee = s.optDouble("startFee", d.startFee),
            driverName = s.optString("driverName", d.driverName),
            organization = s.optString("organization", d.organization),
            reportEmail = s.optString("reportEmail", d.reportEmail),
            mapsApiKey = s.optString("mapsApiKey", d.mapsApiKey),
            regionCode = s.optString("regionCode", d.regionCode),
            numberingYear = s.optInt("numberingYear", d.numberingYear),
            numberingStart = s.optInt("numberingStart", d.numberingStart),
        )
        db.withTransaction {
            db.rides().deleteAll()
            db.customers().deleteAll()
            db.customers().insertAll(customers)
            db.rides().insertAll(rides)
        }
        return settings
    }
}
