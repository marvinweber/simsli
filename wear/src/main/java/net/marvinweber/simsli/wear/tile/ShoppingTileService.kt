package net.marvinweber.simsli.wear.tile

import android.content.Context
import android.net.Uri
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.marvinweber.simsli.wear.R
import net.marvinweber.simsli.wear.common.WEAR_DATA_KEY
import net.marvinweber.simsli.wear.common.WEAR_DATA_PATH
import net.marvinweber.simsli.wear.common.WearDataPayload

private const val RESOURCES_VERSION = "2"
private const val ID_APP_ICON = "app_icon"
private const val ID_ALL_ITEMS_ICON = "all_items_icon"

class ShoppingTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .addIdToImageMapping(
                        ID_APP_ICON,
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(R.mipmap.ic_launcher_round)
                                    .build()
                            )
                            .build()
                    )
                    .addIdToImageMapping(
                        ID_ALL_ITEMS_ICON,
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(R.drawable.ic_tile_all_items)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            "ShoppingTileResources"
        }
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        return CallbackToFutureAdapter.getFuture { completer ->
            serviceScope.launch {
                try {
                    val payload = loadShoppingPayload()
                    val deviceParameters = requestParams.deviceConfiguration
                    val tile = buildTile(payload, deviceParameters)
                    completer.set(tile)
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "ShoppingTile"
        }
    }

    private suspend fun loadShoppingPayload(): WearDataPayload? {
        return try {
            val buffer = Wearable.getDataClient(this)
                .getDataItems(Uri.parse("wear://*$WEAR_DATA_PATH"))
                .await()
            var payload: WearDataPayload? = null
            for (item in buffer) {
                if (item.uri.path == WEAR_DATA_PATH) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val json = dataMap.getString(WEAR_DATA_KEY)
                    if (!json.isNullOrBlank()) {
                        payload = WearDataPayload.fromJson(json)
                        break
                    }
                }
            }
            buffer.release()
            payload
        } catch (e: Exception) {
            null
        }
    }

    private fun buildTile(
        payload: WearDataPayload?,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): TileBuilders.Tile {
        val rootLayout = buildRootLayout(payload, deviceParameters)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                     .setRoot(rootLayout)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildRootLayout(
        payload: WearDataPayload?,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val allStores = payload?.stores ?: emptyList()
        val activeItems = payload?.items?.filter { !it.isDone } ?: emptyList()
        val allActiveCount = activeItems.size

        // Calculate active count per store dynamically and sort descending
        val activeStores = allStores
            .filter { it.id != null }
            .map { store ->
                val count = activeItems.count { it.storeIds.contains(store.id) }
                store.copy(activeCount = count)
            }
            .filter { it.activeCount > 0 }
            .sortedByDescending { it.activeCount }

        val primaryLayout = PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setVerticalSpacerHeight(4f)

        if (allActiveCount == 0) {
            // Empty state: All items completed
            primaryLayout.setPrimaryLabelTextContent(
                Text.Builder(this, "Simsli")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(ColorBuilders.argb(0xFFA5D6A7.toInt()))
                    .build()
            )

            primaryLayout.setContent(
                Text.Builder(this, "All done! 🎉")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build()
            )

            val openAppClickable = createActivityClickable(this, null, null)
            primaryLayout.setPrimaryChipContent(
                CompactChip.Builder(this, "Open Simsli", openAppClickable, deviceParameters)
                    .setChipColors(
                        ChipColors(
                            ColorBuilders.argb(0xFF1E2822.toInt()),
                            ColorBuilders.argb(0xFF81C784.toInt())
                        )
                    )
                    .build()
            )

            return primaryLayout.build()
        }

        // Active items exist: Header shows "Shopping (N)"
        primaryLayout.setPrimaryLabelTextContent(
            Text.Builder(this, "Shopping ($allActiveCount)")
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setColor(ColorBuilders.argb(0xFFA5D6A7.toInt()))
                .build()
        )

        // Store chips: up to 4 stores, 2 per row
        val storesToDisplay = activeStores.take(4)
        if (storesToDisplay.isEmpty()) {
            primaryLayout.setContent(
                Text.Builder(this, "$allActiveCount items to buy")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build()
            )
        } else {
            val columnBuilder = LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.wrap())
                .setHeight(DimensionBuilders.wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

            val storeRows = storesToDisplay.chunked(2)
            storeRows.forEachIndexed { rowIndex, rowStores ->
                if (rowIndex > 0) {
                    columnBuilder.addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(4f))
                            .build()
                    )
                }

                val rowBuilder = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.wrap())
                    .setHeight(DimensionBuilders.wrap())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

                rowStores.forEachIndexed { colIndex, store ->
                    if (colIndex > 0) {
                        rowBuilder.addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setWidth(DimensionBuilders.dp(4f))
                                .build()
                        )
                    }

                    val maxNameLen = 8
                    val displayName = if (store.name.length > maxNameLen) {
                        store.name.take(maxNameLen - 1) + "…"
                    } else {
                        store.name
                    }

                    val storeClickable = createActivityClickable(this, store.id, store.name)
                    val storeChip = LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.wrap())
                        .setHeight(DimensionBuilders.dp(26f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(storeClickable)
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(ColorBuilders.argb(0xFF22282F.toInt()))
                                        .setCorner(
                                            ModifiersBuilders.Corner.Builder()
                                                .setRadius(DimensionBuilders.dp(13f))
                                                .build()
                                        )
                                        .build()
                                )
                                .setPadding(
                                    ModifiersBuilders.Padding.Builder()
                                        .setStart(DimensionBuilders.dp(8f))
                                        .setEnd(DimensionBuilders.dp(8f))
                                        .setTop(DimensionBuilders.dp(4f))
                                        .setBottom(DimensionBuilders.dp(4f))
                                        .build()
                                )
                                .setSemantics(
                                    ModifiersBuilders.Semantics.Builder()
                                        .setContentDescription("${store.name}, ${store.activeCount} items")
                                        .build()
                                )
                                .build()
                        )
                        .addContent(
                            Text.Builder(this, "$displayName (${store.activeCount})")
                                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                .build()
                        )
                        .build()

                    rowBuilder.addContent(storeChip)
                }

                columnBuilder.addContent(rowBuilder.build())
            }

            primaryLayout.setContent(columnBuilder.build())
        }

        // Bottom: Two circular icon buttons side-by-side
        // 1. Left button: Simsli app icon -> opens companion app startpage
        // 2. Right button: All stores checklist icon -> opens full shopping list
        val bottomRowBuilder = LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.wrap())
            .setHeight(DimensionBuilders.wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        val appStartClickable = createActivityClickable(this, null, null)
        val appButton = Button.Builder(this, appStartClickable)
            .setSize(DimensionBuilders.dp(36f))
            .setImageContent(ID_APP_ICON)
            .setButtonColors(
                ButtonColors(
                    ColorBuilders.argb(0x00000000),
                    ColorBuilders.argb(0xFFFFFFFF.toInt())
                )
            )
            .setContentDescription("Open Simsli")
            .build()

        bottomRowBuilder.addContent(appButton)

        bottomRowBuilder.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(12f))
                .build()
        )

        val allStoresClickable = createActivityClickable(this, "all", "All stores")
        val allItemsButton = Button.Builder(this, allStoresClickable)
            .setSize(DimensionBuilders.dp(36f))
            .setIconContent(ID_ALL_ITEMS_ICON, DimensionBuilders.dp(18f))
            .setButtonColors(
                ButtonColors(
                    ColorBuilders.argb(0xFF1E2822.toInt()),
                    ColorBuilders.argb(0xFF81C784.toInt())
                )
            )
            .setContentDescription("All stores ($allActiveCount)")
            .build()

        bottomRowBuilder.addContent(allItemsButton)

        primaryLayout.setPrimaryChipContent(bottomRowBuilder.build())

        return primaryLayout.build()
    }

    private fun createActivityClickable(
        context: Context,
        storeId: String?,
        storeName: String?
    ): ModifiersBuilders.Clickable {
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName("net.marvinweber.simsli.wear.presentation.MainActivity")

        if (storeId != null) {
            activityBuilder.addKeyToExtraMapping(
                "store_id",
                ActionBuilders.AndroidStringExtra.Builder()
                    .setValue(storeId)
                    .build()
            )
        }
        if (storeName != null) {
            activityBuilder.addKeyToExtraMapping(
                "store_name",
                ActionBuilders.AndroidStringExtra.Builder()
                    .setValue(storeName)
                    .build()
            )
        }

        return ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(activityBuilder.build())
                    .build()
            )
            .build()
    }
}
