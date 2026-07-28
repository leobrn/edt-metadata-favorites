/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.navigator.INavigatorContentService;
import org.eclipse.ui.navigator.NavigatorContentServiceFactory;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.v8.dt.md.ui.shared.MdUiSharedImages;


public class FavoritesManagementDialog extends Dialog
{
    private static final String NAVIGATOR_ID = "com._1c.g5.v8.dt.ui2.navigator";

    private static final int MIN_SEARCH_PATTERN_LENGTH = 3;

    private static final int SEARCH_DELAY_MS = 250;

    private static final Object[] NO_EXPANDED_ELEMENTS = new Object[0];

    private static final String NO_CONFIGURATION_MESSAGE =
        "Не удалось получить конфигурацию этого проекта - возможно, это не проект 1С:EDT.";

    private final List<String> projectNames;

    private String currentProject;

    private List<FavoriteTreeNode> currentRoots = List.of();


    private final Map<String, Map<String, PendingChange>> pendingByProject = new LinkedHashMap<>();

    private final Map<String, Set<String>> originallyCheckedByProject = new LinkedHashMap<>();

    private final Map<String, Set<String>> checkedByProject = new LinkedHashMap<>();

    private final Map<String, TreeBuildResult> treeByProject = new LinkedHashMap<>();

    private Combo projectCombo;

    private Text searchText;

    private int searchGeneration;

    private Job searchJob;

    private Runnable pendingSearch;

    private boolean searchActive;

    private Label statusLabel;

    private String projectStatusMessage = "";

    private Button selectAllButton;

    private Button deselectAllButton;

    private TreeViewer treeViewer;

    private final TreeVisibilityFilter visibilityFilter = new TreeVisibilityFilter();

    private FavoriteTreeLabelProvider favoriteTreeLabelProvider;

    private final Map<EClass, Image> imageByClass = new IdentityHashMap<>();

    private Image titleImage;

    private INavigatorContentService navigatorContentService;

    private ILabelProvider navigatorLabelProvider;


    private record PendingChange(PinTarget target, boolean pin)
    {
    }


    public FavoritesManagementDialog(Shell parentShell, List<String> projectNames, String initialProject)
    {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE);
        this.projectNames = projectNames;
        this.currentProject = initialProject;
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);
        newShell.setText("Избранное");
        titleImage = AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/lock.png").createImage();
        newShell.setImage(titleImage);
    }

    @Override
    protected Point getInitialSize()
    {
        return new Point(520, 560);
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = (Composite)super.createDialogArea(parent);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        area.setLayout(layout);

        createProjectRow(area);
        createSearchRow(area);
        createFilterRow(area);
        createStatusRow(area);
        createTree(area);

        loadProject(currentProject);
        return area;
    }

    private void createProjectRow(Composite parent)
    {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label label = new Label(row, SWT.NONE);
        label.setText("Проект:");

        projectCombo = new Combo(row, SWT.READ_ONLY);
        projectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (String name : projectNames)
        {
            projectCombo.add(name);
        }
        int index = projectNames.indexOf(currentProject);
        projectCombo.select(index < 0 ? 0 : index);
        projectCombo.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                switchProject(projectCombo.getText());
            }
        });
    }

    private void createSearchRow(Composite parent)
    {
        searchText = new Text(parent, SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.setMessage("Поиск по дереву метаданных");
        searchText.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (e.detail == SWT.ICON_CANCEL)
                {
                    searchText.setText("");
                }
            }
        });
        searchText.addModifyListener(e -> scheduleSearch(searchText.getText()));
    }

    private void scheduleSearch(String pattern)
    {
        cancelPendingSearch();
        int generation = ++searchGeneration;
        pendingSearch = new Runnable()
        {
            @Override
            public void run()
            {
                if (pendingSearch != this)
                {
                    return;
                }
                pendingSearch = null;
                applySearch(generation, pattern);
            }
        };
        searchText.getDisplay().timerExec(SEARCH_DELAY_MS, pendingSearch);
        updateSearchUiState();
    }

    private void cancelPendingSearch()
    {
        if (pendingSearch != null && searchText != null && !searchText.isDisposed())
        {
            searchText.getDisplay().timerExec(-1, pendingSearch);
        }
        pendingSearch = null;
    }

    private void applySearch(int generation, String pattern)
    {
        if (generation != searchGeneration || searchText == null || searchText.isDisposed()
            || treeViewer == null || treeViewer.getControl().isDisposed())
        {
            return;
        }
        String normalizedPattern = normalizeSearchPattern(pattern);
        String effectivePattern = effectiveSearchPattern(normalizedPattern);
        boolean hasPattern = !effectivePattern.isEmpty();
        if (!hasPattern && !searchActive)
        {
            updateSearchUiState();
            return;
        }

        cancelSearchJob();
        searchActive = hasPattern;
        if (!hasPattern)
        {
            visibilityFilter.clearSearch();
            setSearchHighlighting(false);
            treeViewer.getControl().setRedraw(false);
            try
            {
                treeViewer.refresh(true);
                if (visibilityFilter.isOnlySelected() && shouldAutoExpandSelected())
                {
                    setExpandedMatchingElements();
                }
                else
                {
                    treeViewer.setExpandedElements(NO_EXPANDED_ELEMENTS);
                }
            }
            finally
            {
                treeViewer.getControl().setRedraw(true);
            }
            updateSearchUiState();
            return;
        }

        List<FavoriteTreeNode> roots = currentRoots;
        String projectName = currentProject;
        boolean onlySelected = visibilityFilter.isOnlySelected();
        Set<String> selectedUuids =
            onlySelected ? Set.copyOf(checkedUuids(projectName)) : Set.of();
        var display = searchText.getDisplay();
        searchJob = new Job("Поиск в дереве избранного")
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(roots, effectivePattern,
                    onlySelected, selectedUuids, monitor::isCanceled);
                if (result == null || monitor.isCanceled())
                {
                    return Status.CANCEL_STATUS;
                }
                if (display.isDisposed())
                {
                    return Status.CANCEL_STATUS;
                }
                display.asyncExec(
                    () -> applySearchResult(generation, projectName, roots, effectivePattern, result));
                return Status.OK_STATUS;
            }
        };
        searchJob.setSystem(true);
        searchJob.schedule();
        updateSearchUiState();
    }

    private void applySearchResult(int generation, String projectName, List<FavoriteTreeNode> roots,
        String pattern, FavoriteTreeSearch.Result result)
    {
        if (generation != searchGeneration || !projectName.equals(currentProject)
            || roots != currentRoots || treeViewer == null || treeViewer.getControl().isDisposed())
        {
            return;
        }
        searchJob = null;
        visibilityFilter.applySearch(pattern, result);
        setSearchHighlighting(true);
        treeViewer.getControl().setRedraw(false);
        try
        {
            treeViewer.refresh(true);
            if (result.matchingObjects() <= FavoriteUiLimits.MAX_AUTO_EXPANDED_OBJECTS)
            {
                setExpandedMatchingElements();
            }
            else
            {
                treeViewer.setExpandedElements(NO_EXPANDED_ELEMENTS);
            }
        }
        finally
        {
            treeViewer.getControl().setRedraw(true);
        }
        updateSearchUiState();
    }

    private void cancelSearchJob()
    {
        if (searchJob != null)
        {
            searchJob.cancel();
            searchJob = null;
        }
    }

    private void setExpandedMatchingElements()
    {
        List<FavoriteTreeNode> expanded = new ArrayList<>();
        currentRoots.forEach(node -> collectExpandedMatchingElements(node, expanded));
        treeViewer.setExpandedElements(expanded.toArray());
    }

    private void collectExpandedMatchingElements(FavoriteTreeNode node,
        List<FavoriteTreeNode> expanded)
    {
        boolean hasVisibleChildren =
            node.children.stream().anyMatch(visibilityFilter::matchesSubtree);
        if (!hasVisibleChildren)
        {
            return;
        }
        expanded.add(node);
        node.children.stream()
            .filter(visibilityFilter::matchesSubtree)
            .forEach(child -> collectExpandedMatchingElements(child, expanded));
    }


    private void createFilterRow(Composite parent)
    {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(3, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button onlySelectedButton = new Button(row, SWT.CHECK);
        onlySelectedButton.setText("Только выбранные");
        onlySelectedButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        onlySelectedButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                visibilityFilter.setOnlySelected(onlySelectedButton.getSelection());
                if (searchActive)
                {
                    restartSearch();
                }
                else
                {
                    refreshSelectedVisibility();
                }
                updateSearchUiState();
            }
        });

        selectAllButton = new Button(row, SWT.PUSH);
        selectAllButton.setText("Выбрать все");
        selectAllButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setAllChecked(true);
            }
        });

        deselectAllButton = new Button(row, SWT.PUSH);
        deselectAllButton.setText("Снять все");
        deselectAllButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setAllChecked(false);
            }
        });
        updateSearchUiState();
    }


    private void createStatusRow(Composite parent)
    {
        statusLabel = new Label(parent, SWT.WRAP);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createTree(Composite parent)
    {
        treeViewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.CHECK);
        treeViewer.setUseHashlookup(true);
        Tree tree = treeViewer.getTree();
        tree.setHeaderVisible(false);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 260;
        treeViewer.getControl().setLayoutData(data);
        treeViewer.setContentProvider(new FavoriteTreeContentProvider());
        navigatorLabelProvider = createNavigatorLabelProvider();

        TreeViewerColumn nameColumn = new TreeViewerColumn(treeViewer, SWT.NONE);
        nameColumn.getColumn().setWidth(390);
        favoriteTreeLabelProvider = new FavoriteTreeLabelProvider(navigatorLabelProvider);
        favoriteTreeLabelProvider.setOwnerDrawEnabled(false);
        nameColumn.setLabelProvider(favoriteTreeLabelProvider);

        treeViewer.addFilter(visibilityFilter);
        Runnable resizeColumns =
            () -> nameColumn.getColumn().setWidth(Math.max(220, tree.getClientArea().width));
        tree.addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent e)
            {
                resizeColumns.run();
            }
        });
        tree.getDisplay().asyncExec(() -> {
            if (!tree.isDisposed())
            {
                resizeColumns.run();
            }
        });

        tree.addListener(SWT.Selection, event -> {
            if (event.detail != SWT.CHECK || !(event.item instanceof TreeItem item))
            {
                return;
            }
            if (item.getData() instanceof FavoriteTreeNode node)
            {
                toggleFavorite(node, item);
            }
        });

        tree.addListener(SWT.KeyDown, event -> {
            if (event.character != ' ')
            {
                return;
            }
            TreeItem[] selection = tree.getSelection();
            if (selection.length > 0
                && selection[0].getData() instanceof FavoriteTreeNode node)
            {
                toggleFavorite(node, selection[0]);
                event.doit = false;
            }
        });
    }


    private ILabelProvider createNavigatorLabelProvider()
    {
        try
        {
            navigatorContentService =
                NavigatorContentServiceFactory.INSTANCE.createContentService(NAVIGATOR_ID, treeViewer);
            return navigatorContentService.createCommonLabelProvider();
        }
        catch (RuntimeException e)
        {
            if (navigatorContentService != null)
            {
                navigatorContentService.dispose();
                navigatorContentService = null;
            }
            Activator.logError("Не удалось получить стандартные иконки Навигатора EDT", e);
            return null;
        }
    }

    static FavoriteState favoriteState(int total, int checked)
    {
        if (total <= 0 || checked <= 0)
        {
            return FavoriteState.NONE;
        }
        return checked >= total ? FavoriteState.ALL : FavoriteState.PARTIAL;
    }

    private FavoriteState favoriteState(FavoriteTreeNode node)
    {
        if (node.isGroup())
        {
            CheckSummary summary = checkSummary(node);
            return favoriteState(summary.total(), summary.checked());
        }
        return favoriteState(1, effectiveChecked(node) ? 1 : 0);
    }

    private void toggleFavorite(FavoriteTreeNode node, TreeItem item)
    {
        boolean changed;
        if (node.isGroup())
        {
            CheckSummary summary = checkSummary(node);
            changed = summary.total() > 0
                && setSubtreeChecked(node, summary.checked() < summary.total());
        }
        else
        {
            changed = recordChange(node, !effectiveChecked(node));
        }
        if (!changed)
        {
            updateFavoriteState(item);
            return;
        }

        updateCheckSummaries(node);
        treeViewer.getControl().setRedraw(false);
        try
        {
            if (node.isGroup())
            {
                updateMaterializedFavoriteState(item);
            }
            else
            {
                updateFavoriteState(item);
            }
            updateParentFavoriteStates(node);
            refreshAfterFavoriteStateChange();
        }
        finally
        {
            treeViewer.getControl().setRedraw(true);
        }
    }

    private void setAllChecked(boolean checked)
    {
        boolean changed = false;
        for (FavoriteTreeNode group : currentRoots)
        {
            changed |= visibilityFilter.isFiltered()
                ? setVisibleSubtreeChecked(group, checked)
                : setSubtreeChecked(group, checked);
        }
        if (!changed)
        {
            return;
        }
        treeViewer.getControl().setRedraw(false);
        try
        {
            updateMaterializedFavoriteState();
            refreshAfterFavoriteStateChange();
        }
        finally
        {
            treeViewer.getControl().setRedraw(true);
        }
    }

    private boolean setVisibleSubtreeChecked(FavoriteTreeNode node, boolean checked)
    {
        if (!visibilityFilter.matchesSubtree(node))
        {
            return false;
        }
        boolean changed =
            visibilityFilter.matchesObject(node) && recordChange(node, checked);
        for (FavoriteTreeNode child : node.children)
        {
            changed |= setVisibleSubtreeChecked(child, checked);
        }
        recomputeCheckSummary(node);
        return changed;
    }

    private boolean setSubtreeChecked(FavoriteTreeNode node, boolean checked)
    {
        boolean changed = node.isObject() && recordChange(node, checked);
        for (FavoriteTreeNode child : node.children)
        {
            changed |= setSubtreeChecked(child, checked);
        }
        recomputeCheckSummary(node);
        return changed;
    }

    private void updateCheckSummaries(FavoriteTreeNode node)
    {
        recomputeCheckSummary(node);
        for (FavoriteTreeNode parent = node.parent; parent != null; parent = parent.parent)
        {
            recomputeCheckSummary(parent);
        }
    }

    private CheckSummary recomputeCheckSummary(FavoriteTreeNode node)
    {
        int total = node.isObject() ? 1 : 0;
        int checked = node.isObject() && effectiveChecked(node) ? 1 : 0;
        for (FavoriteTreeNode child : node.children)
        {
            CheckSummary childSummary = checkSummary(child);
            total += childSummary.total();
            checked += childSummary.checked();
        }
        CheckSummary result = new CheckSummary(total, checked);
        checkSummaries.put(node, result);
        return result;
    }

    private void updateMaterializedFavoriteState()
    {
        for (TreeItem item : treeViewer.getTree().getItems())
        {
            updateMaterializedFavoriteState(item);
        }
    }

    private void updateMaterializedFavoriteState(TreeItem item)
    {
        updateFavoriteState(item);
        for (TreeItem child : item.getItems())
        {
            updateMaterializedFavoriteState(child);
        }
    }

    private void updateFavoriteState(TreeItem item)
    {
        if (item.getData() instanceof FavoriteTreeNode node)
        {
            FavoriteState state = favoriteState(node);
            item.setChecked(state != FavoriteState.NONE);
            item.setGrayed(state == FavoriteState.PARTIAL);
        }
    }

    private void updateParentFavoriteStates(FavoriteTreeNode node)
    {
        for (FavoriteTreeNode parent = node.parent; parent != null; parent = parent.parent)
        {
            treeViewer.update(parent, null);
        }
    }

    private void refreshAfterFavoriteStateChange()
    {
        if (!visibilityFilter.isOnlySelected())
        {
            return;
        }
        if (searchActive)
        {
            scheduleSearch(searchText.getText());
        }
        else
        {
            refreshSelectedVisibility();
        }
    }

    private void restartSearch()
    {
        cancelPendingSearch();
        int generation = ++searchGeneration;
        applySearch(generation, searchText.getText());
    }

    private void refreshSelectedVisibility()
    {
        treeViewer.getControl().setRedraw(false);
        try
        {
            boolean autoExpand =
                visibilityFilter.isOnlySelected() && shouldAutoExpandSelected();
            treeViewer.refresh(true);
            if (visibilityFilter.isOnlySelected())
            {
                if (autoExpand)
                {
                    setExpandedMatchingElements();
                }
                else
                {
                    treeViewer.setExpandedElements(NO_EXPANDED_ELEMENTS);
                }
            }
        }
        finally
        {
            treeViewer.getControl().setRedraw(true);
        }
    }

    private boolean shouldAutoExpandSelected()
    {
        return checkedUuids(currentProject).size() <= FavoriteUiLimits.MAX_AUTO_EXPANDED_OBJECTS;
    }

    private void switchProject(String projectName)
    {
        if (projectName.equals(currentProject))
        {
            return;
        }
        currentProject = projectName;
        loadProject(projectName);
    }

    private void loadProject(String projectName)
    {
        cancelPendingSearch();
        cancelSearchJob();
        int generation = ++searchGeneration;
        TreeBuildResult buildResult =
            treeByProject.computeIfAbsent(projectName, FavoritesManagementDialog::buildTree);
        currentRoots = buildResult.roots();
        imageByClass.clear();
        initializeCheckState(projectName, buildResult.configurationUuid());
        visibilityFilter.clearSearch();
        searchActive = false;
        setSearchHighlighting(false);
        projectStatusMessage = statusMessageFor(buildResult);
        treeViewer.setInput(currentRoots);

        String pattern = searchText == null ? "" : searchText.getText();
        if (pattern.trim().length() >= MIN_SEARCH_PATTERN_LENGTH)
        {
            applySearch(generation, pattern);
        }
        else if (visibilityFilter.isOnlySelected() && shouldAutoExpandSelected())
        {
            setExpandedMatchingElements();
        }
        updateSearchUiState();
    }


    private static String statusMessageFor(TreeBuildResult buildResult)
    {
        if (!buildResult.configurationAvailable())
        {
            return NO_CONFIGURATION_MESSAGE;
        }
        return "";
    }

    private void setStatusMessage(String message)
    {
        statusLabel.setText(message == null ? "" : message);
        statusLabel.getParent().layout(true, true);
    }

    private void setSearchHighlighting(boolean enabled)
    {
        if (favoriteTreeLabelProvider != null
            && favoriteTreeLabelProvider.isOwnerDrawEnabled() != enabled)
        {
            favoriteTreeLabelProvider.setOwnerDrawEnabled(enabled);
        }
    }

    private boolean effectiveChecked(FavoriteTreeNode object)
    {
        return checkedUuids(currentProject).contains(object.target.uuid());
    }

    private final Map<FavoriteTreeNode, CheckSummary> checkSummaries = new IdentityHashMap<>();

    private record CheckSummary(int total, int checked)
    {
    }

    enum FavoriteState
    {
        NONE,
        PARTIAL,
        ALL
    }


    private CheckSummary checkSummary(FavoriteTreeNode node)
    {
        CheckSummary cached = checkSummaries.get(node);
        if (cached != null)
        {
            return cached;
        }
        return recomputeCheckSummary(node);
    }

    private void initializeCheckState(String projectName, String configurationUuid)
    {
        if (checkedByProject.containsKey(projectName))
        {
            rebuildCheckSummaries();
            return;
        }
        Set<String> originallyChecked = new LinkedHashSet<>();
        PinStore.ProjectPinSnapshot pinSnapshot =
            Activator.getDefault().getPinStore().getProjectPinSnapshot(projectName);
        checkSummaries.clear();
        for (FavoriteTreeNode root : currentRoots)
        {
            initializeCheckState(root, configurationUuid, pinSnapshot, originallyChecked);
        }
        originallyCheckedByProject.put(projectName, Set.copyOf(originallyChecked));
        checkedByProject.put(projectName, new LinkedHashSet<>(originallyChecked));
    }

    private CheckSummary initializeCheckState(FavoriteTreeNode node, String configurationUuid,
        PinStore.ProjectPinSnapshot snapshot, Set<String> originallyChecked)
    {
        int total = node.isObject() ? 1 : 0;
        int checked = 0;
        if (node.isObject() && snapshot.isEffectivelyPinned(uuidPath(node, configurationUuid)))
        {
            originallyChecked.add(node.target.uuid());
            checked = 1;
        }
        for (FavoriteTreeNode child : node.children)
        {
            CheckSummary childSummary =
                initializeCheckState(child, configurationUuid, snapshot, originallyChecked);
            total += childSummary.total();
            checked += childSummary.checked();
        }
        CheckSummary summary = new CheckSummary(total, checked);
        checkSummaries.put(node, summary);
        return summary;
    }

    static List<String> uuidPath(FavoriteTreeNode object, String configurationUuid)
    {
        List<String> result = new ArrayList<>();
        for (FavoriteTreeNode current = object; current != null; current = current.parent)
        {
            if (current.isObject())
            {
                result.add(current.target.uuid());
            }
        }
        if (configurationUuid != null && !configurationUuid.isBlank())
        {
            result.add(configurationUuid);
        }
        return result;
    }

    static String searchHighlightPattern(String pattern)
    {
        int separator = pattern.lastIndexOf('.');
        return separator < 0 ? pattern : pattern.substring(separator + 1);
    }

    private static String normalizeSearchPattern(String pattern)
    {
        return pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
    }

    private static String effectiveSearchPattern(String normalizedPattern)
    {
        return normalizedPattern.length() >= MIN_SEARCH_PATTERN_LENGTH ? normalizedPattern : "";
    }

    static boolean searchViewUpdating(String requestedPattern, String appliedPattern,
        boolean searchPending, boolean searchRunning)
    {
        if (searchRunning)
        {
            return true;
        }
        if (!searchPending)
        {
            return false;
        }
        String effectivePattern =
            effectiveSearchPattern(normalizeSearchPattern(requestedPattern));
        return !effectivePattern.equals(appliedPattern);
    }

    private boolean searchViewUpdating()
    {
        String requestedPattern = searchText == null ? "" : searchText.getText();
        return searchViewUpdating(requestedPattern, visibilityFilter.pattern(),
            pendingSearch != null, searchJob != null);
    }

    record BulkActionState(boolean selectAllEnabled, boolean deselectAllEnabled)
    {
    }

    static BulkActionState bulkActionState(boolean searchUpdating, boolean onlySelected)
    {
        return new BulkActionState(!searchUpdating && !onlySelected, !searchUpdating);
    }

    static String searchStatusMessage(String requestedPattern, String appliedPattern,
        int matchingObjects, String projectMessage, boolean searchUpdating)
    {
        if (searchUpdating)
        {
            return "Поиск\u2026";
        }
        String normalizedPattern = normalizeSearchPattern(requestedPattern);
        if (!normalizedPattern.isEmpty()
            && normalizedPattern.length() < MIN_SEARCH_PATTERN_LENGTH)
        {
            return "Введите не менее " + MIN_SEARCH_PATTERN_LENGTH + " символов для поиска.";
        }
        if (!appliedPattern.isEmpty())
        {
            return "Найдено объектов: " + matchingObjects;
        }
        return projectMessage == null ? "" : projectMessage;
    }

    private void updateSearchUiState()
    {
        updateBulkActionState();
        updateSearchStatus();
    }

    private void updateSearchStatus()
    {
        if (statusLabel == null || statusLabel.isDisposed())
        {
            return;
        }
        String requestedPattern = searchText == null ? "" : searchText.getText();
        String message = searchStatusMessage(requestedPattern, visibilityFilter.pattern(),
            visibilityFilter.matchingObjectCount(), projectStatusMessage, searchViewUpdating());
        if (!message.equals(statusLabel.getText()))
        {
            setStatusMessage(message);
        }
    }

    private void updateBulkActionState()
    {
        if (selectAllButton == null || selectAllButton.isDisposed()
            || deselectAllButton == null || deselectAllButton.isDisposed())
        {
            return;
        }

        boolean searchUpdating = searchViewUpdating();
        boolean onlySelected = visibilityFilter.isOnlySelected();
        BulkActionState state = bulkActionState(searchUpdating, onlySelected);
        selectAllButton.setEnabled(state.selectAllEnabled());
        deselectAllButton.setEnabled(state.deselectAllEnabled());

        if (searchUpdating)
        {
            selectAllButton.setToolTipText("Дождитесь завершения поиска");
            deselectAllButton.setToolTipText("Дождитесь завершения поиска");
            return;
        }

        boolean searchApplied = !visibilityFilter.pattern().isEmpty();
        if (onlySelected)
        {
            selectAllButton.setToolTipText("Все объекты текущего отбора уже выбраны");
            deselectAllButton.setToolTipText(searchApplied
                ? "Снять отметки со всех найденных выбранных объектов"
                : "Снять отметки со всех выбранных объектов, показанных в форме");
        }
        else if (searchApplied)
        {
            selectAllButton.setToolTipText("Отметить все найденные объекты");
            deselectAllButton.setToolTipText("Снять отметки со всех найденных объектов");
        }
        else
        {
            selectAllButton.setToolTipText("Отметить все объекты текущего проекта");
            deselectAllButton.setToolTipText("Снять отметки со всех объектов текущего проекта");
        }
    }

    private Set<String> checkedUuids(String projectName)
    {
        return checkedByProject.getOrDefault(projectName, Set.of());
    }

    private void rebuildCheckSummaries()
    {
        checkSummaries.clear();
        currentRoots.forEach(this::checkSummary);
    }

    private boolean recordChange(FavoriteTreeNode object, boolean checked)
    {
        Set<String> selected = checkedByProject.get(currentProject);
        if (selected == null)
        {
            return false;
        }
        String uuid = object.target.uuid();
        boolean changed = checked ? selected.add(uuid) : selected.remove(uuid);
        if (!changed)
        {
            return false;
        }

        boolean originallyPinned =
            originallyCheckedByProject.getOrDefault(currentProject, Set.of()).contains(uuid);
        Map<String, PendingChange> projectPending = pendingByProject.get(currentProject);
        if (checked == originallyPinned)
        {
            if (projectPending != null)
            {
                projectPending.remove(uuid);
                if (projectPending.isEmpty())
                {
                    pendingByProject.remove(currentProject);
                }
            }
        }
        else
        {
            if (projectPending == null)
            {
                projectPending = new LinkedHashMap<>();
                pendingByProject.put(currentProject, projectPending);
            }
            projectPending.put(uuid, new PendingChange(object.target, checked));
        }
        return true;
    }

    @Override
    protected void okPressed()
    {
        applyPendingChanges();
        ToggleFilterHandler.enableFilter(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
        super.okPressed();
    }


    private void applyPendingChanges()
    {
        if (pendingByProject.isEmpty())
        {
            return;
        }
        PinStore store = Activator.getDefault().getPinStore();
        for (Map.Entry<String, Map<String, PendingChange>> projectEntry : pendingByProject.entrySet())
        {
            String projectName = projectEntry.getKey();
            List<PinTarget> toPin = new ArrayList<>();
            List<PinTarget> toUnpin = new ArrayList<>();
            for (PendingChange change : projectEntry.getValue().values())
            {
                (change.pin() ? toPin : toUnpin).add(change.target());
            }
            if (!toPin.isEmpty())
            {
                store.pinObjects(projectName, toPin);
            }
            if (!toUnpin.isEmpty())
            {
                store.unpinObjects(projectName, toUnpin);
            }
        }
        pendingByProject.clear();
        PinOperations.refreshUi();
    }


    @Override
    public boolean close()
    {
        if (!pendingByProject.isEmpty() && !confirmDiscard())
        {
            return false;
        }
        pendingByProject.clear();
        cancelPendingSearch();
        cancelSearchJob();
        boolean closed = super.close();
        if (closed && titleImage != null)
        {
            titleImage.dispose();
            titleImage = null;
        }
        if (closed && navigatorLabelProvider != null)
        {
            navigatorLabelProvider.dispose();
            navigatorLabelProvider = null;
        }
        if (closed && navigatorContentService != null)
        {
            navigatorContentService.dispose();
            navigatorContentService = null;
        }
        return closed;
    }

    private boolean confirmDiscard()
    {
        MessageDialog dialog = new MessageDialog(getShell(), "Несохранённые изменения", null,
            "В избранном есть несохранённые изменения. Закрыть без сохранения?", MessageDialog.WARNING,
            new String[] {"Продолжить", "Закрыть без сохранения"}, 0);
        return dialog.open() == 1;
    }


    private record TreeBuildResult(List<FavoriteTreeNode> roots, boolean configurationAvailable,
        String configurationUuid)
    {
    }


    private static TreeBuildResult buildTree(String projectName)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        Configuration configuration = MetadataPinSupport.getConfiguration(project);
        if (configuration == null)
        {
            return new TreeBuildResult(List.of(), false, null);
        }

        FavoriteTreeModel.BuildResult buildResult = FavoriteTreeModel.build(configuration);

        return new TreeBuildResult(buildResult.roots(), true, MetadataPinSupport.getUuid(configuration));
    }

    private final class FavoriteTreeContentProvider implements ITreeContentProvider
    {
        @Override
        @SuppressWarnings("unchecked")
        public Object[] getElements(Object inputElement)
        {
            return ((List<FavoriteTreeNode>)inputElement).toArray();
        }

        @Override
        public Object[] getChildren(Object parentElement)
        {
            return ((FavoriteTreeNode)parentElement).children.toArray();
        }

        @Override
        public Object getParent(Object element)
        {
            return ((FavoriteTreeNode)element).parent;
        }

        @Override
        public boolean hasChildren(Object element)
        {
            if (!(element instanceof FavoriteTreeNode node))
            {
                return false;
            }
            return node.children.stream().anyMatch(visibilityFilter::matchesSubtree);
        }
    }

    private final class FavoriteTreeLabelProvider extends StyledCellLabelProvider
    {
        private final ILabelProvider navigatorLabelProvider;

        FavoriteTreeLabelProvider(ILabelProvider navigatorLabelProvider)
        {
            this.navigatorLabelProvider = navigatorLabelProvider;
        }

        @Override
        public void update(ViewerCell cell)
        {
            if (!(cell.getElement() instanceof FavoriteTreeNode node))
            {
                super.update(cell);
                return;
            }
            cell.setText(node.label);
            cell.setImage(imageFor(node));
            cell.setStyleRanges(highlightRanges(node.normalizedLabel,
                searchHighlightPattern(visibilityFilter.pattern())));
            if (cell.getItem() instanceof TreeItem item)
            {
                FavoriteState state = favoriteState(node);
                item.setChecked(state != FavoriteState.NONE);
                item.setGrayed(state == FavoriteState.PARTIAL);
            }
        }

        private Image imageFor(FavoriteTreeNode node)
        {
            if (node.isGroup() && FavoriteTreeModel.COMMON_GROUP.equals(node.label))
            {
                return MdUiSharedImages.getImage(MdUiSharedImages.OBJS_COMMON);
            }
            if (navigatorLabelProvider == null)
            {
                return null;
            }
            MdObject mdObject = node.mdObject;
            if (mdObject == null && !node.children.isEmpty())
            {
                mdObject = node.firstMdObject();
            }
            if (mdObject == null)
            {
                return null;
            }
            EClass type = mdObject.eClass();
            if (imageByClass.containsKey(type))
            {
                return imageByClass.get(type);
            }
            Image image = navigatorLabelProvider.getImage(mdObject);
            imageByClass.put(type, image);
            return image;
        }

        private StyleRange[] highlightRanges(String normalizedLabel, String pattern)
        {
            if (pattern.isEmpty())
            {
                return null;
            }
            List<StyleRange> ranges = new ArrayList<>();
            int from = 0;
            int index;
            while ((index = normalizedLabel.indexOf(pattern, from)) >= 0)
            {
                ranges.add(new StyleRange(index, pattern.length(), null, null, SWT.BOLD));
                from = index + pattern.length();
            }
            return ranges.isEmpty() ? null : ranges.toArray(StyleRange[]::new);
        }
    }


    private final class TreeVisibilityFilter extends ViewerFilter
    {
        private String pattern = "";

        private boolean onlySelected;

        private Set<FavoriteTreeNode> searchVisibleNodes = Set.of();

        private Set<FavoriteTreeNode> searchMatchingObjectNodes = Set.of();

        private int matchingObjectCount;

        void applySearch(String value, FavoriteTreeSearch.Result result)
        {
            pattern = value;
            searchVisibleNodes = result.visibleNodes();
            searchMatchingObjectNodes = result.matchingObjectNodes();
            matchingObjectCount = result.matchingObjects();
        }

        void clearSearch()
        {
            pattern = "";
            searchVisibleNodes = Set.of();
            searchMatchingObjectNodes = Set.of();
            matchingObjectCount = 0;
        }

        void setOnlySelected(boolean value)
        {
            onlySelected = value;
        }

        boolean isOnlySelected()
        {
            return onlySelected;
        }

        String pattern()
        {
            return pattern;
        }

        int matchingObjectCount()
        {
            return matchingObjectCount;
        }

        boolean isFiltered()
        {
            return onlySelected || !pattern.isEmpty();
        }

        boolean matchesObject(FavoriteTreeNode node)
        {
            if (!node.isObject())
            {
                return false;
            }
            if (!pattern.isEmpty())
            {
                return searchMatchingObjectNodes.contains(node);
            }
            return !onlySelected || effectiveChecked(node);
        }

        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element)
        {
            FavoriteTreeNode node = (FavoriteTreeNode)element;
            return matchesSubtree(node);
        }

        boolean matchesSubtree(FavoriteTreeNode node)
        {
            if (pattern.isEmpty())
            {
                return !onlySelected || checkSummary(node).checked() > 0;
            }
            return searchVisibleNodes.contains(node);
        }
    }
}
