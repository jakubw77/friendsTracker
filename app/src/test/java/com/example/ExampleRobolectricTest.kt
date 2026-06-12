package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Friend Locator", appName)
  }

  @Test
  fun `verify MapCalculations coordinate distance`() {
    // Distance between Warsaw Central (52.2297, 21.0118) and a point ~10km East (52.2297, 21.1584)
    val distance = MapCalculations.calculateDistanceKm(52.2297, 21.0118, 52.2297, 21.1584)
    // Roughly 10 km
    assert(distance in 5.0..15.0)
  }

  @Test
  fun `verify auto-refresh countdown ticker and reset`() {
    val viewModel = LocationViewModel()
    
    // Initial countdown value should be 30 seconds
    assertEquals(30, viewModel.nextRefreshSeconds.value)
    
    // Decrement once
    var triggered = false
    viewModel.decrementRefreshCountdown {
        triggered = true
    }
    assertEquals(29, viewModel.nextRefreshSeconds.value)
    assert(!triggered)
    
    // Fast-forward countdown down to 1
    for (i in 1..28) {
        viewModel.decrementRefreshCountdown {}
    }
    assertEquals(1, viewModel.nextRefreshSeconds.value)
    
    // One more tick should invoke the trigger lambda and reset countdown to 30
    viewModel.decrementRefreshCountdown {
        triggered = true
    }
    assertEquals(30, viewModel.nextRefreshSeconds.value)
    assert(triggered)
  }

  @Test
  fun `verify distance unit metric conversions and preferences`() {
    val viewModel = LocationViewModel()
    
    // Default should be Kilometers
    assertEquals(DistanceUnit.KILOMETERS, viewModel.distanceUnit.value)
    
    // Change to Miles
    viewModel.setDistanceUnit(DistanceUnit.MILES)
    assertEquals(DistanceUnit.MILES, viewModel.distanceUnit.value)
    
    // Check conversion logic
    val kmDistance = 10.0
    val milesDistance = MapCalculations.convertKmToUnit(kmDistance, DistanceUnit.MILES)
    // 10 km should be ~6.21 miles
    assertEquals(6.21371, milesDistance, 0.001)

    val backToKm = MapCalculations.convertKmToUnit(kmDistance, DistanceUnit.KILOMETERS)
    assertEquals(10.0, backToKm, 0.001)
  }

  @Test
  fun `verify distance matrix friend properties and api key validation`() {
    val friend = Friend(
        id = "f_test",
        name = "Test Friend",
        email = "test@gmail.com",
        initial = "TF",
        latitude = 52.2,
        longitude = 21.0,
        avatarColorHex = "#FFFFFF",
        customDrivingDistanceText = "15.3 km",
        customDrivingDurationText = "22 mins",
        isLiveTrafficFetched = true
    )

    assertEquals("15.3 km", friend.customDrivingDistanceText)
    assertEquals("22 mins", friend.customDrivingDurationText)
    assertEquals(true, friend.isLiveTrafficFetched)

    val viewModel = LocationViewModel()
    assertEquals("", viewModel.mapsApiKey.value)

    viewModel.setMapsApiKey("TEST_API_KEY_123")
    assertEquals("TEST_API_KEY_123", viewModel.mapsApiKey.value)
  }
}
