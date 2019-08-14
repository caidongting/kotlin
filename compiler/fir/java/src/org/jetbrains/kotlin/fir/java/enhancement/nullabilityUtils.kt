/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java.enhancement

import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.resolvedFqName
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.load.java.*
import org.jetbrains.kotlin.load.java.typeEnhancement.NullabilityQualifier
import org.jetbrains.kotlin.load.java.typeEnhancement.NullabilityQualifierWithMigrationStatus
import org.jetbrains.kotlin.utils.JavaTypeEnhancementState
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

fun List<FirAnnotationCall>.extractNullability(
    annotationTypeQualifierResolver: FirAnnotationTypeQualifierResolver,
    javaTypeEnhancementState: JavaTypeEnhancementState
): NullabilityQualifierWithMigrationStatus? =
    this.firstNotNullResult { annotationCall ->
        annotationCall.extractNullability(annotationTypeQualifierResolver, javaTypeEnhancementState)
    }


fun FirAnnotationCall.extractNullability(
    annotationTypeQualifierResolver: FirAnnotationTypeQualifierResolver,
    javaTypeEnhancementState: JavaTypeEnhancementState
): NullabilityQualifierWithMigrationStatus? {
    this.extractNullabilityFromKnownAnnotations(javaTypeEnhancementState)?.let { return it }

    val typeQualifierAnnotation =
        annotationTypeQualifierResolver.resolveTypeQualifierAnnotation(this)
            ?: return null

    val jsr305ReportLevel = annotationTypeQualifierResolver.resolveJsr305ReportLevel(this)
    if (jsr305ReportLevel.isIgnore) return null

    return typeQualifierAnnotation.extractNullabilityFromKnownAnnotations(javaTypeEnhancementState)?.copy(isForWarningOnly = jsr305ReportLevel.isWarning)
}

private fun FirAnnotationCall.extractNullabilityFromKnownAnnotations(javaTypeEnhancementState: JavaTypeEnhancementState): NullabilityQualifierWithMigrationStatus? {
    val annotationFqName = resolvedFqName ?: return null

    return when {
        annotationFqName in NULLABLE_ANNOTATIONS -> NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NULLABLE)
        annotationFqName in NOT_NULL_ANNOTATIONS -> NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NOT_NULL)
        annotationFqName == JAVAX_NONNULL_ANNOTATION -> extractNullabilityTypeFromArgument()

        annotationFqName == COMPATQUAL_NULLABLE_ANNOTATION && javaTypeEnhancementState.enableCompatqualCheckerFrameworkAnnotations ->
            NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NULLABLE)

        annotationFqName == COMPATQUAL_NONNULL_ANNOTATION && javaTypeEnhancementState.enableCompatqualCheckerFrameworkAnnotations ->
            NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NOT_NULL)

        annotationFqName == ANDROIDX_RECENTLY_NON_NULL_ANNOTATION -> NullabilityQualifierWithMigrationStatus(
            NullabilityQualifier.NOT_NULL,
            isForWarningOnly = true
        )

        annotationFqName == ANDROIDX_RECENTLY_NULLABLE_ANNOTATION -> NullabilityQualifierWithMigrationStatus(
            NullabilityQualifier.NULLABLE,
            isForWarningOnly = true
        )
        else -> null
    }
}

private fun FirAnnotationCall.extractNullabilityTypeFromArgument(): NullabilityQualifierWithMigrationStatus? {
    val enumValue = this.arguments.firstOrNull()?.toResolvedCallableSymbol()?.callableId?.callableName
    // if no argument is specified, use default value: NOT_NULL
        ?: return NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NOT_NULL)

    return when (enumValue.asString()) {
        "ALWAYS" -> NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NOT_NULL)
        "MAYBE", "NEVER" -> NullabilityQualifierWithMigrationStatus(NullabilityQualifier.NULLABLE)
        "UNKNOWN" -> NullabilityQualifierWithMigrationStatus(NullabilityQualifier.FORCE_FLEXIBILITY)
        else -> null
    }
}
