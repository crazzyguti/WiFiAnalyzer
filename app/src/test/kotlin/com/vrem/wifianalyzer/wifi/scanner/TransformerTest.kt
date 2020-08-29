/*
 * WiFiAnalyzer
 * Copyright (C) 2015 - 2020 VREM Software Development <VREMSoftwareDevelopment@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.vrem.wifianalyzer.wifi.scanner

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import com.nhaarman.mockitokotlin2.*
import com.vrem.wifianalyzer.wifi.model.*
import com.vrem.wifianalyzer.wifi.model.WiFiWidth
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TransformerTest {
    private val scanResult1 = withScanResult(SSID_1, BSSID_1, WiFiWidth.MHZ_160, WiFiStandard.AX)
    private val scanResult2 = withScanResult(SSID_2, BSSID_2, WiFiWidth.MHZ_80, WiFiStandard.AC)
    private val scanResult3 = withScanResult(SSID_3, BSSID_3, WiFiWidth.MHZ_40, WiFiStandard.N)
    private val cacheResults = listOf(
            CacheResult(scanResult1, scanResult1.level),
            CacheResult(scanResult2, scanResult2.level),
            CacheResult(scanResult3, scanResult2.level))
    private val wifiInfo = withWiFiInfo()
    private val fixture = spy(Transformer())

    @After
    fun tearDown() {
        verifyNoMoreInteractions(wifiInfo)
    }

    @Test
    fun testTransformWithNulls() {
        // execute
        val actual = fixture.transformWifiInfo(null)
        // validate
        assertEquals(WiFiConnection.EMPTY, actual)
    }

    @Test
    fun testTransformWifiInfoNotConnected() {
        // setup
        whenever(wifiInfo.networkId).thenReturn(-1)
        // execute
        val actual = fixture.transformWifiInfo(wifiInfo)
        // validate
        assertEquals(WiFiConnection.EMPTY, actual)
        verify(wifiInfo).networkId
    }

    @Test
    fun testTransformScanResults() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        doReturn(true).whenever(fixture).minVersionR()
        // execute
        val actual = fixture.transformCacheResults(cacheResults)
        // validate
        assertEquals(cacheResults.size, actual.size)
        validateWiFiDetail(SSID_1, BSSID_1, WiFiWidth.MHZ_160, WiFiStandard.AX, actual[0])
        validateWiFiDetail(SSID_2, BSSID_2, WiFiWidth.MHZ_80, WiFiStandard.AC, actual[1])
        validateWiFiDetail(SSID_3, BSSID_3, WiFiWidth.MHZ_40, WiFiStandard.N, actual[2])
        verify(fixture, times(9)).minVersionM()
        verify(fixture, times(3)).minVersionR()
    }

    @Test
    fun testWiFiData() {
        // setup
        val expectedWiFiConnection = WiFiConnection(WiFiIdentifier(SSID_1, BSSID_1), IP_ADDRESS, LINK_SPEED)
        // execute
        val actual = fixture.transformToWiFiData(cacheResults, wifiInfo)
        // validate
        assertEquals(expectedWiFiConnection, actual.wiFiConnection)
        assertEquals(cacheResults.size, actual.wiFiDetails.size)
        verifyWiFiInfo()
    }

    @Test
    fun testChannelWidth() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        // execute
        val actual = fixture.channelWidth(scanResult1)
        // validate
        assertEquals(WiFiWidth.MHZ_160.channelWidth, actual)
        verify(fixture).minVersionM()
    }

    @Test
    fun testChannelWidthLegacy() {
        // execute
        val actual = fixture.channelWidth(scanResult1)
        // validate
        assertEquals(WiFiWidth.MHZ_20.channelWidth, actual)
    }

    @Test
    fun testWiFiStandard() {
        // setup
        doReturn(true).whenever(fixture).minVersionR()
        // execute
        val actual = fixture.wiFiStandard(scanResult1)
        // validate
        assertEquals(WiFiStandard.AX.wiFiStandardId, actual)
        verify(fixture).minVersionR()
    }

    @Test
    fun testWiFiStandardLegacy() {
        // execute
        val actual = fixture.wiFiStandard(scanResult1)
        // validate
        assertEquals(WiFiStandard.UNKNOWN.wiFiStandardId, actual)
    }

    @Test
    fun testMc80211() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        whenever(scanResult1.is80211mcResponder).thenReturn(true)
        // execute
        val actual = fixture.mc80211(scanResult1)
        // validate
        assertTrue(actual)
        verify(scanResult1).is80211mcResponder
        verify(fixture).minVersionM()
    }

    @Test
    fun testMc80211Legacy() {
        // execute
        val actual = fixture.mc80211(scanResult1)
        // validate
        assertFalse(actual)
    }

    @Test
    fun testCenterFrequency() {
        // setup
        // execute
        val actual = fixture.centerFrequency(scanResult1, WiFiWidth.MHZ_20)
        // validate
        assertEquals(FREQUENCY, actual)
    }

    @Test
    fun testCenterFrequencyWithFrequency() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        val expected = FREQUENCY + WiFiWidth.MHZ_20.frequencyWidthHalf
        scanResult1.centerFreq0 = FREQUENCY + WiFiWidth.MHZ_20.frequencyWidthHalf
        // execute
        val actual = fixture.centerFrequency(scanResult1, WiFiWidth.MHZ_20)
        // validate
        assertEquals(expected, actual)
    }

    @Test
    fun testCenterFrequencyWithExtFrequencyAfter() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        val expected = FREQUENCY + WiFiWidth.MHZ_20.frequencyWidthHalf
        scanResult1.centerFreq0 = FREQUENCY + WiFiWidth.MHZ_40.frequencyWidthHalf
        // execute
        val actual = fixture.centerFrequency(scanResult1, WiFiWidth.MHZ_40)
        // validate
        assertEquals(expected, actual)
    }

    @Test
    fun testCenterFrequencyWithExtFrequencyBefore() {
        // setup
        doReturn(true).whenever(fixture).minVersionM()
        val expected = FREQUENCY - WiFiWidth.MHZ_20.frequencyWidthHalf
        scanResult1.frequency = FREQUENCY
        scanResult1.centerFreq0 = FREQUENCY - WiFiWidth.MHZ_40.frequencyWidthHalf
        // execute
        val actual = fixture.centerFrequency(scanResult1, WiFiWidth.MHZ_40)
        // validate
        assertEquals(expected, actual)
    }

    @Test
    fun testIsExtensionFrequencyWith2GHz() {
        // setup
        val frequency = FREQUENCY
        // execute & validate
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY - WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidth))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY - WiFiWidth.MHZ_40.frequencyWidth))
    }

    @Test
    fun testIsExtensionFrequencyWith5GHz() {
        // setup
        val frequency = 5100
        // execute & validate
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY - WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidth))
        assertTrue(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_40, FREQUENCY - WiFiWidth.MHZ_40.frequencyWidth))
    }

    @Test
    fun testIsNotExtensionFrequency() {
        // setup
        val frequency = FREQUENCY
        // execute & validate
        assertFalse(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_20, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertFalse(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_80, FREQUENCY + WiFiWidth.MHZ_40.frequencyWidthHalf))
        assertFalse(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_20, FREQUENCY + WiFiWidth.MHZ_20.frequencyWidthHalf))
        assertFalse(fixture.extensionFrequency(frequency, WiFiWidth.MHZ_80, FREQUENCY + WiFiWidth.MHZ_20.frequencyWidthHalf))
    }

    private fun withScanResult(ssid: SSID, bssid: BSSID, wiFiWidth: WiFiWidth, wiFiStandard: WiFiStandard): ScanResult {
        val scanResult: ScanResult = mock()
        scanResult.SSID = ssid
        scanResult.BSSID = bssid
        scanResult.capabilities = WPA
        scanResult.frequency = FREQUENCY
        scanResult.level = LEVEL
        scanResult.channelWidth = wiFiWidth.ordinal
        whenever(scanResult.wifiStandard).thenReturn(wiFiStandard.wiFiStandardId)
        return scanResult
    }

    private fun withWiFiInfo(): WifiInfo {
        val wifiInfo: WifiInfo = mock()
        whenever(wifiInfo.networkId).thenReturn(0)
        whenever(wifiInfo.ssid).thenReturn(SSID_1)
        whenever(wifiInfo.bssid).thenReturn(BSSID_1)
        whenever(wifiInfo.ipAddress).thenReturn(IP_ADDRESS_VALUE)
        whenever(wifiInfo.linkSpeed).thenReturn(LINK_SPEED)
        return wifiInfo
    }

    private fun verifyWiFiInfo() {
        verify(wifiInfo).networkId
        verify(wifiInfo).ssid
        verify(wifiInfo).bssid
        verify(wifiInfo).ipAddress
        verify(wifiInfo).linkSpeed
    }

    private fun validateWiFiDetail(SSID: String, BSSID: String, wiFiWidth: WiFiWidth, wiFiStandard: WiFiStandard, wiFiDetail: WiFiDetail) {
        assertEquals(SSID, wiFiDetail.wiFiIdentifier.ssid)
        assertEquals(BSSID, wiFiDetail.wiFiIdentifier.bssid)
        assertEquals(WPA, wiFiDetail.capabilities)
        with(wiFiDetail.wiFiSignal) {
            assertEquals(FREQUENCY, this.primaryFrequency)
            assertEquals(FREQUENCY, this.centerFrequency)
            assertEquals(LEVEL, this.level)
            assertEquals(wiFiWidth, this.wiFiWidth)
            assertEquals(wiFiStandard, this.wiFiStandard)
        }
    }

    companion object {
        private const val SSID_1 = "SSID_1-123"
        private const val BSSID_1 = "BSSID_1-123"
        private const val SSID_2 = "SSID_2-123"
        private const val BSSID_2 = "BSSID_2-123"
        private const val SSID_3 = "SSID_3-123"
        private const val BSSID_3 = "BSSID_3-123"
        private const val WPA = "WPA"
        private const val FREQUENCY = 2435
        private const val LEVEL = -40
        private const val IP_ADDRESS_VALUE = 123456789
        private const val IP_ADDRESS = "21.205.91.7"
        private const val LINK_SPEED = 21
    }
}