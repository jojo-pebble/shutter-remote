package com.jojo.pebbleshutter

import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Receives presses from the watch (Shutter Remote watch app) through the Pebble app
 * and tells the watch whether the shutter was pressed.
 */
class ShutterListenerService : BasePebbleListenerService() {

    companion object {
        val WATCHAPP_UUID: UUID = UUID.fromString("4ef3430e-3ba5-442a-baef-b75bc300942c")

        // The same numbers are used in src/c/main.c on the watch
        private const val KEY_COMMAND = 1u
        private const val KEY_RESULT = 2u
        private const val CMD_SHOOT = 1
        private const val RES_SERVICE_OFF = 4
    }

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != WATCHAPP_UUID) return ReceiveResult.Nack

        val command = when (val item = data[KEY_COMMAND]) {
            is PebbleDictionaryItem.UInt32 -> item.value.toInt()
            is PebbleDictionaryItem.Int32 -> item.value
            is PebbleDictionaryItem.UInt8 -> item.value.toInt()
            is PebbleDictionaryItem.Int8 -> item.value.toInt()
            else -> null
        }
        if (command != CMD_SHOOT) return ReceiveResult.Ack

        val service = ShutterService.instance
        val code = if (service == null) {
            ShutterService.log("Command from the watch, but the service is off")
            RES_SERVICE_OFF
        } else {
            ShutterService.log("Command from the watch: shoot")
            service.pressShutter(ShutterService.Mode.AUTO).code
        }

        // The reply is sent separately so the acknowledgement is not delayed
        coroutineScope.launch { reply(watch, code) }
        return ReceiveResult.Ack
    }

    private suspend fun reply(watch: WatchIdentifier, code: Int) {
        val sender = DefaultPebbleSender(this)
        try {
            val result = sender.sendDataToPebble(
                WATCHAPP_UUID,
                mapOf(KEY_RESULT to PebbleDictionaryItem.UInt8(code)),
                listOf(watch)
            )
            if (result == null) ShutterService.log("Could not reply to the watch: the Pebble app is unavailable")
        } catch (e: Exception) {
            ShutterService.log("Error replying to the watch: ${e.message}")
        } finally {
            sender.close()
        }
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == WATCHAPP_UUID) ShutterService.log("Shutter Remote opened on the watch")
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == WATCHAPP_UUID) ShutterService.log("Shutter Remote closed on the watch")
    }
}
