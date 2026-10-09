package com.shilapi.xcertplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodecNamesTest {
    @Test fun aospAndThirdPartySoftwareCodecsAreRecognised() {
        for (name in listOf(
            "OMX.google.h264.decoder",
            "c2.android.avc.decoder",
            "OMX.ffmpeg.h264",
            "OMX.PV.avcdec",
            "OMX.vendor.sw.avc",
        )) {
            assertTrue(name, VideoCodecNames.isSoftware(name))
        }
    }

    /**
     * Every one of these is some vendor's hardware block. Which name the CS11 actually exposes is
     * not known here, so the rule under test is the negative one: a vendor codec must never be
     * mistaken for software, or the hardware path is skipped on exactly the devices that need it.
     */
    @Test fun vendorHardwareCodecsAreNotMistakenForSoftware() {
        for (name in listOf(
            "OMX.FSL.AVC.Decoder",
            "OMX.fsl.video.dec.avc",
            "OMX.qcom.video.decoder.avc",
            "OMX.Intel.hw_vd.h264",
            "OMX.NVIDIA.h264.decode",
            "OMX.MTK.VIDEO.DECODER.AVC",
            "OMX.rk.video_decoder.avc",
            "OMX.hisi.video.decoder.avc",
        )) {
            assertFalse(name, VideoCodecNames.isSoftware(name))
        }
    }
}
