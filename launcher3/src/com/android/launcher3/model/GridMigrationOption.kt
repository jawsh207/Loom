/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.model

/**
 * This has all the valid grids as data objects so that we can check if we're going to be able to do
 * a migration. In order to be able to do the migration the source grid and destination grid both
 * have to be instantiated here, and it has to be a valid destination from that particular source
 * grid. We purposely don't allow all grid sizes on all device types. e.g: Grid migrations from a
 * unique-to-tablet configuration (6x5) to a unique-to-phone configuration (4x5) and vice-versa
 * might occur when restoring from one of these device to another, but can't be manually switched by
 * a user).
 *
 * @param columns is the number of columns of the grid.
 * @param rows is the number of rows of the grid.
 */
sealed class GridMigrationOption(val columns: Int, val rows: Int) {

    /**
     * Method that checks if we are able to migrate from one grid to another.
     *
     * @param destGridMigrationOption is the grid migration option we're going to.
     * @param isAfterRestore is a boolean that is true when in a backup and restore scenario.
     * @return a boolean that lets us know whether we can migrate from source to destination.
     */
    fun canMigrate(destGridMigrationOption: GridMigrationOption, isAfterRestore: Boolean): Boolean {
        // We check if the destination grid is a valid destination for the current grid, or if
        // we're in a restore scenario, in which case we allow any existing grid as a
        // destination.
        // Folio: grids can be any size from Folio's settings (a phone can be 6x5 too), and the
        // migration logic works for any pair of sizes, so every migration is allowed.
        if (FOLIO_ANY_SIZE) return true
        return validDestinations.contains(destGridMigrationOption) || isAfterRestore
    }

    private val validDestinations: List<GridMigrationOption>
        get() =
            when (this) {
                TwoByTwo,
                ThreeByThree,
                FourByFour,
                FourByFive,
                FourBySix,
                FiveByFive,
                FiveBySix,
                EightByThree,
                SevenByThree,
                is Custom -> validDestinationsForPhone
                SixByFive -> validDestinationsForTablet
            }

    private val validDestinationsForPhone: List<GridMigrationOption>
        get() =
            listOf(
                TwoByTwo,
                ThreeByThree,
                FourByFour,
                FourByFive,
                FourBySix,
                FiveByFive,
                FiveBySix,
                EightByThree,
                SevenByThree,
            )

    private val validDestinationsForTablet: List<GridMigrationOption>
        get() = listOf(SixByFive)

    data object TwoByTwo : GridMigrationOption(columns = 2, rows = 2)

    data object ThreeByThree : GridMigrationOption(columns = 3, rows = 3)

    data object FourByFour : GridMigrationOption(columns = 4, rows = 4)

    data object FourByFive : GridMigrationOption(columns = 4, rows = 5)

    data object FourBySix : GridMigrationOption(columns = 4, rows = 6)

    data object FiveByFive : GridMigrationOption(columns = 5, rows = 5)

    data object FiveBySix : GridMigrationOption(columns = 5, rows = 6)

    data object SixByFive : GridMigrationOption(columns = 6, rows = 5)

    data object EightByThree : GridMigrationOption(columns = 8, rows = 3)

    data object SevenByThree : GridMigrationOption(columns = 7, rows = 3)

    /** Folio: a phone grid size chosen in Folio's settings. */
    data class Custom(val c: Int, val r: Int) : GridMigrationOption(columns = c, rows = r)

    companion object {
        private const val FOLIO_ANY_SIZE = true

        /**
         * Factory method that creates an instance of GridMigrationOption if valid.
         *
         * @param columns is the number of columns of the grid.
         * @param rows is the number of rows of the grid.
         */
        fun from(columns: Int, rows: Int): GridMigrationOption? =
            when {
                columns == 2 && rows == 2 -> TwoByTwo
                columns == 3 && rows == 3 -> ThreeByThree
                columns == 4 && rows == 4 -> FourByFour
                columns == 4 && rows == 5 -> FourByFive
                columns == 4 && rows == 6 -> FourBySix
                columns == 5 && rows == 5 -> FiveByFive
                columns == 5 && rows == 6 -> FiveBySix
                columns == 6 && rows == 5 -> SixByFive
                columns == 8 && rows == 3 -> EightByThree
                columns == 7 && rows == 3 -> SevenByThree
                columns in 2..10 && rows in 2..12 -> Custom(columns, rows)
                else -> null
            }
    }
}
