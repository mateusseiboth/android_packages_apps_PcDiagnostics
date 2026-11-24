package org.lineageos.pcdiagnostics.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.Page
import org.lineageos.pcdiagnostics.R

private class Destination(val title: Int, val subtitle: Int, val icon: ImageVector)

private val DESTINATIONS = linkedMapOf(
    Page.OVERVIEW to Destination(R.string.page_overview, R.string.page_overview_subtitle, Icons.Outlined.Dashboard),
    Page.CONNECTIVITY to Destination(R.string.page_connectivity, R.string.page_connectivity_subtitle, Icons.Outlined.Wifi),
    Page.INPUT to Destination(R.string.page_input, R.string.page_input_subtitle, Icons.Outlined.Keyboard),
)

private val TASK_MANAGER = Destination(R.string.page_task_manager, R.string.page_task_manager_subtitle, Icons.Outlined.Memory)

private val PAGES: Map<Page, @Composable (DiagnosticsViewModel, (Intent) -> Unit) -> Unit> = mapOf(
    Page.OVERVIEW to { viewModel, _ -> OverviewPage(viewModel) },
    Page.CONNECTIVITY to { viewModel, onOpen -> ConnectivityPage(viewModel, onOpen) },
    Page.INPUT to { viewModel, _ -> InputPage(viewModel) },
)

private val WIDE_WINDOW = 840.dp
private val PAGE_MAX_WIDTH = 1280.dp
private val DRAWER_PADDING = PaddingValues(start = 24.dp, top = 36.dp, end = 24.dp, bottom = 24.dp)
private val CONTENT_PADDING = PaddingValues(start = 16.dp, top = 36.dp, end = 16.dp)

/** The drawer, given what to do after a pick (close it when it floats). */
private typealias DrawerContent = @Composable (onPicked: () -> Unit) -> Unit

/** A wide window keeps the drawer open beside the page; a narrow one opens it from a menu button. */
private val LAYOUTS: Map<Boolean, @Composable (DrawerContent, @Composable () -> Unit) -> Unit> = mapOf(
    true to { drawer, content -> PermanentLayout(drawer, content) },
    false to { drawer, content -> ModalLayout(drawer, content) },
)

@Composable
fun DiagnosticsApp(viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LAYOUTS.getValue(maxWidth >= WIDE_WINDOW)(
                { onPicked -> Drawer(page, viewModel, onOpen, onPicked) },
                { PageFrame(DESTINATIONS.getValue(page)) { PAGES.getValue(page)(viewModel, onOpen) } },
            )
        }
    }
}

@Composable
private fun PermanentLayout(drawer: DrawerContent, content: @Composable () -> Unit) {
    PermanentNavigationDrawer(
        drawerContent = { PermanentDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) { drawer {} } },
        content = content,
    )
}

@Composable
private fun ModalLayout(drawer: DrawerContent, content: @Composable () -> Unit) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = state,
        drawerContent = { ModalDrawerSheet { drawer { scope.launch { state.close() } } } },
    ) {
        Column {
            IconButton(onClick = { scope.launch { state.open() } }, modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.navigation_menu))
            }
            content()
        }
    }
}

@Composable
private fun Drawer(page: Page, viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit, onPicked: () -> Unit) {
    Column(Modifier.padding(DRAWER_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.app_name), Modifier.padding(start = 16.dp, bottom = 16.dp), style = MaterialTheme.typography.titleLarge)
        DESTINATIONS.forEach { (target, destination) ->
            DrawerItem(destination, selected = target == page) {
                viewModel.onPageSelected(target)
                onPicked()
            }
        }
        viewModel.taskManagerIntent?.let { intent -> DrawerItem(TASK_MANAGER, selected = false) { onOpen(intent) } }
    }
}

@Composable
private fun DrawerItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.takeIf { selected } ?: Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(destination.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text(stringResource(destination.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(destination.subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The page's icon and title above its content, centered and at most [PAGE_MAX_WIDTH] wide. */
@Composable
private fun PageFrame(destination: Destination, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(CONTENT_PADDING), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PAGE_MAX_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(destination.icon, contentDescription = null, modifier = Modifier.padding(horizontal = 9.dp).size(25.dp))
                Text(stringResource(destination.title), style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}
