package com.example.nameplateexcel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameplateResultNormalizerTest {
    @Test
    fun keepsValuesFromLineOrientedAdxPlateAndRejectsPtc() {
        val result = NameplateResultNormalizer.normalize(mapOf(
            "ip_rating" to "IP 68",
            "efficiency" to "",
            "rated_power" to "0.40kW",
            "rated_current" to "2,6 A",
            "explosion_protection" to "PTC",
            "rated_speed" to "1400 1/min",
            "power_factor" to "cosφ 0,42",
            "phases" to "3~",
            "rated_voltage" to "Y 380 V",
        ))

        assertEquals("IP68", result.values["ip_rating"])
        assertEquals("0.4 kW", result.values["rated_power"])
        assertEquals("2.6 A", result.values["rated_current"])
        assertEquals("", result.values["explosion_protection"])
        assertEquals("1400 1/min", result.values["rated_speed"])
        assertEquals("0.42", result.values["power_factor"])
        assertEquals("3", result.values["phases"])
        assertEquals("Y 380 V", result.values["rated_voltage"])
        assertTrue("explosion_protection" in result.rejectedFields)
    }

    @Test
    fun keepsNordenhamTableValuesInTheirOwnColumns() {
        val result = NameplateResultNormalizer.normalize(mapOf(
            "ip_rating" to "IP55",
            "efficiency" to "",
            "rated_power" to "11 kW",
            "rated_current" to "6,6/12,1 A",
            "explosion_protection" to "1Ex d IIB T4",
            "rated_speed" to "2925 min-1",
            "power_factor" to "0,89",
            "phases" to "3",
            "rated_voltage" to "Δ380/Y660 V",
        ))

        assertEquals("11 kW", result.values["rated_power"])
        assertEquals("6.6/12.1 A", result.values["rated_current"])
        assertEquals("1Ex d IIB T4", result.values["explosion_protection"])
        assertEquals("2925 1/min", result.values["rated_speed"])
        assertEquals("0.89", result.values["power_factor"])
        assertEquals("Δ380/Y660 V", result.values["rated_voltage"])
        assertTrue(result.rejectedFields.isEmpty())
    }

    @Test
    fun separatesVemTableHeadersAndEfficiencyClass() {
        val result = NameplateResultNormalizer.normalize(mapOf(
            "ip_rating" to "IP55",
            "efficiency" to "IE2-87,6",
            "rated_power" to "4 kW",
            "rated_current" to "14,4/8,4 A",
            "explosion_protection" to "Ex",
            "rated_speed" to "1460 min⁻¹",
            "power_factor" to "cos φ 0,83",
            "phases" to "3-ph",
            "rated_voltage" to "Δ220/Y380 V",
        ))

        assertEquals("87.6 %", result.values["efficiency"])
        assertEquals("4 kW", result.values["rated_power"])
        assertEquals("14.4/8.4 A", result.values["rated_current"])
        assertEquals("", result.values["explosion_protection"])
        assertEquals("1460 1/min", result.values["rated_speed"])
        assertEquals("0.83", result.values["power_factor"])
        assertEquals("3", result.values["phases"])
        assertEquals("Δ220/Y380 V", result.values["rated_voltage"])
        assertTrue("explosion_protection" in result.rejectedFields)
    }

    @Test
    fun rejectsKnownColumnMixupsInsteadOfWritingThemToExcel() {
        val result = NameplateResultNormalizer.normalize(mapOf(
            "efficiency" to "11",
            "rated_power" to "1460 min-1",
            "rated_current" to "50 Hz",
            "explosion_protection" to "Ex",
            "rated_speed" to "4 kW",
            "power_factor" to "IE2-87,6",
        ))

        assertEquals("", result.values["efficiency"])
        assertEquals("", result.values["rated_power"])
        assertEquals("", result.values["rated_current"])
        assertEquals("", result.values["explosion_protection"])
        assertEquals("", result.values["rated_speed"])
        assertEquals("", result.values["power_factor"])
        assertEquals(6, result.rejectedFields.size)
    }
}
