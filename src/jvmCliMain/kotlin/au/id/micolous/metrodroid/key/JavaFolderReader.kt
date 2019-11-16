/*
 * SmartCard.kt
 *
 * Copyright 2019 Michael Farrell <micolous+git@gmail.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.micolous.metrodroid.key

import java.io.File

class JavaFolderReader(private val folder: File) : CardKeysFileReader {
    init {
        require(folder.isDirectory) { "must be directory" }
    }

    override fun readFile(fileName: String)
        = File(folder, fileName).readText()

    override fun listFiles(dir: String)
        = folder.listFiles { _, it ->
        it.endsWith(".json")  }!!.map { it.name }

}
