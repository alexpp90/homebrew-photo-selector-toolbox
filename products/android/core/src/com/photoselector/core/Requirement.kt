package com.photoselector.core

/**
 * Maps a test method or class to one or more formal requirement IDs
 * in docs/products/android-desktop/REQUIREMENTS.md or docs/products/phototok/REQUIREMENTS.md.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Requirement(vararg val ids: String)
