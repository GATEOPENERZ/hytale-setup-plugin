package com.gateopenerz.hytaleserver

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface HytaleServerExtension {
    val serverDir: Property<String>
    val channel: Property<String>
    val version: Property<String>
    val jvmArgs: ListProperty<String>
}