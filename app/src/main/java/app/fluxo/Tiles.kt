package app.fluxo

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class FluxoTile(private val n: String) : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Fluxo $n"; updateTile() }
    }

    override fun onClick() {
        Store.init(applicationContext)
        Engine.onEvent(applicationContext, "tile", mapOf("n" to n))
    }
}

class Tile1 : FluxoTile("1")
class Tile2 : FluxoTile("2")
class Tile3 : FluxoTile("3")
