package com.notnex.neurorecognition.detection

class InterpreterResultHolder(val detections: Int) {

    val outputLocations = Array(1) { Array(detections) { FloatArray(4) } }
    val outputClasses = Array(1) { FloatArray(detections) }
    val outputScores = Array(1) { FloatArray(detections) }
    private val numDetections = FloatArray(1)

    fun  createOutputMap() = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetections
    )

}