package com.burixer85.cs2skinflip.core.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FindActivityTest {

    @Test
    fun `returns the context itself when it is already an Activity`() {
        val activity = mock<Activity>()

        assertSame(activity, activity.findActivity())
    }

    @Test
    fun `unwraps a ContextWrapper to find the underlying Activity`() {
        val activity = mock<Activity>()
        val wrapper = mock<ContextWrapper>()
        whenever(wrapper.baseContext).thenReturn(activity)

        assertSame(activity, wrapper.findActivity())
    }

    @Test
    fun `unwraps nested ContextWrappers`() {
        val activity = mock<Activity>()
        val innerWrapper = mock<ContextWrapper>()
        whenever(innerWrapper.baseContext).thenReturn(activity)
        val outerWrapper = mock<ContextWrapper>()
        whenever(outerWrapper.baseContext).thenReturn(innerWrapper)

        assertSame(activity, outerWrapper.findActivity())
    }

    @Test
    fun `returns null when no Activity is found in the wrapper chain`() {
        val applicationContext = mock<Context>()
        val wrapper = mock<ContextWrapper>()
        whenever(wrapper.baseContext).thenReturn(applicationContext)

        assertNull(wrapper.findActivity())
    }
}
