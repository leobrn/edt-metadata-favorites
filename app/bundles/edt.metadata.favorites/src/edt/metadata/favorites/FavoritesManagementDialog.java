/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
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
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.navigator.INavigatorContentService;
import org.eclipse.ui.navigator.NavigatorContentServiceFactory;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.v8.dt.md.ui.shared.MdUiSharedImages;


public class FavoritesManagementDialog extends Dialog
{
    private static final String NAVIGATOR_ID = "com._1c.g5.v8.dt.ui2.navigator";

    private static final int MIN_SEARCH_PATTERN_LENGTH = 2;

    private static final int MAX_AUTO_EXPANDED_SEARCH_RESULTS = 300;


    private static final String NO_CONFIGURATION_MESSAGE =
        "Не удалось получить конфигурацию этого проекта - возможно, это не проект 1С:EDT.";

    private final List<String> projectNames;

    private String currentProject;

    private List<FavoriteTreeNode> currentRoots = List.of();


    private final Map<String, Map<String, PendingChange>> pendingByProject = new LinkedHashMap<>();

    private Combo projectCombo;

    private Text searchText;


    private int searchGeneration;


    private boolean searchActive;

    private Label statusLabel;

    private CheckboxTreeViewer treeViewer;

    private final TreeVisibilityFilter visibilityFilter = new TreeVisibilityFilter();


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
        searchText.addModifyListener(e -> {
            int generation = ++searchGeneration;
            String pattern = searchText.getText();
            searchText.getDisplay().timerExec(150, () -> applySearch(generation, pattern));
        });
    }


    private void applySearch(int generation, String pattern)
    {
        if (generation != searchGeneration || searchText == null || searchText.isDisposed()
            || treeViewer == null || treeViewer.getControl().isDisposed())
        {
            return;
        }
        String effectivePattern = pattern.trim().length() >= MIN_SEARCH_PATTERN_LENGTH ? pattern : "";
        boolean hasPattern = !effectivePattern.isEmpty();
        if (!hasPattern && !searchActive)
        {
            return;
        }
        treeViewer.getControl().setRedraw(false);
        try
        {
            searchActive = hasPattern;
            visibilityFilter.setPattern(effectivePattern);
            if (hasPattern)
            {
                treeViewer.collapseAll();
                treeViewer.refresh();
                if (matchingObjectCount() <= MAX_AUTO_EXPANDED_SEARCH_RESULTS)
                {
                    currentRoots.forEach(this::expandSearchMatches);
                }
            }
            else
            {
                treeViewer.setInput(currentRoots);
                if (visibilityFilter.isOnlySelected())
                {
                    currentRoots.forEach(this::expandMatchingBranches);
                }
            }
        }
        finally
        {
            treeViewer.getControl().setRedraw(true);
        }
    }

    private int matchingObjectCount()
    {
        return currentRoots.stream().mapToInt(this::matchingObjectCount).sum();
    }

    private int matchingObjectCount(FavoriteTreeNode node)
    {
        int count = visibilityFilter.labelMatches(node) ? 1 : 0;
        for (FavoriteTreeNode child : node.children)
        {
            count += matchingObjectCount(child);
        }
        return count;
    }

    private void expandMatchingBranches(FavoriteTreeNode node)
    {
        if (node.children.isEmpty() || !visibilityFilter.matchesSubtree(node))
        {
            return;
        }
        treeViewer.setExpandedState(node, true);
        node.children.forEach(this::expandMatchingBranches);
    }


    private void expandSearchMatches(FavoriteTreeNode node)
    {
        if (node.children.isEmpty()
            || node.children.stream().noneMatch(visibilityFilter::labelMatchesSubtree))
        {
            return;
        }
        treeViewer.setExpandedState(node, true);
        node.children.forEach(this::expandSearchMatches);
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
                treeViewer.refresh();
                if (onlySelectedButton.getSelection())
                {
                    currentRoots.forEach(FavoritesManagementDialog.this::expandMatchingBranches);
                }
            }
        });

        Button selectAll = new Button(row, SWT.PUSH);
        selectAll.setText("Выбрать все");
        selectAll.setToolTipText("Отметить все объекты текущего проекта");
        selectAll.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setAllChecked(true);
            }
        });

        Button deselectAll = new Button(row, SWT.PUSH);
        deselectAll.setText("Снять все");
        deselectAll.setToolTipText("Снять отметку со всех объектов текущего проекта");
        deselectAll.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setAllChecked(false);
            }
        });
    }


    private void createStatusRow(Composite parent)
    {
        statusLabel = new Label(parent, SWT.WRAP);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createTree(Composite parent)
    {
        treeViewer = new CheckboxTreeViewer(parent, SWT.BORDER);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 260;
        treeViewer.getControl().setLayoutData(data);
        treeViewer.setContentProvider(new FavoriteTreeContentProvider());
        navigatorLabelProvider = createNavigatorLabelProvider();
        treeViewer.setLabelProvider(new FavoriteTreeLabelProvider(navigatorLabelProvider));
        treeViewer.addFilter(visibilityFilter);
        treeViewer.setCheckStateProvider(new ICheckStateProvider()
        {
            @Override
            public boolean isChecked(Object element)
            {
                if (!(element instanceof FavoriteTreeNode node))
                {
                    return false;
                }
                if (node.isObject())
                {
                    return effectiveChecked(node);
                }
                return checkSummary(node).checked() > 0;
            }

            @Override
            public boolean isGrayed(Object element)
            {
                if (!(element instanceof FavoriteTreeNode node) || !node.isGroup())
                {
                    return false;
                }
                CheckSummary summary = checkSummary(node);
                return summary.checked() > 0 && summary.checked() < summary.total();
            }
        });
        treeViewer.addCheckStateListener(new ICheckStateListener()
        {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event)
            {
                if (!(event.getElement() instanceof FavoriteTreeNode node))
                {
                    return;
                }
                if (node.isGroup())
                {
                    node.forEachObject(object -> recordChange(object, event.getChecked()));
                }
                else
                {
                    recordChange(node, event.getChecked());
                }
                invalidateViewCaches();
                treeViewer.refresh();
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


    private void setAllChecked(boolean checked)
    {
        for (FavoriteTreeNode group : currentRoots)
        {
            group.forEachObject(object -> recordChange(object, checked));
        }
        invalidateViewCaches();
        treeViewer.refresh();
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
        TreeBuildResult buildResult = buildTree(projectName);
        currentRoots = buildResult.roots();
        invalidateViewCaches();
        searchActive = !visibilityFilter.pattern().isEmpty();
        statusLabel.setText(statusMessageFor(buildResult));
        treeViewer.setInput(currentRoots);
        if (visibilityFilter.isOnlySelected())
        {
            currentRoots.forEach(this::expandMatchingBranches);
        }
        else if (!visibilityFilter.pattern().isEmpty())
        {
            currentRoots.forEach(this::expandSearchMatches);
        }
    }


    private static String statusMessageFor(TreeBuildResult buildResult)
    {
        if (!buildResult.configurationAvailable())
        {
            return NO_CONFIGURATION_MESSAGE;
        }
        return "";
    }


    private boolean effectiveChecked(FavoriteTreeNode object)
    {
        Map<String, PendingChange> pending = pendingByProject.getOrDefault(currentProject, Map.of());
        PendingChange change = pending.get(object.target.uuid());
        return change != null ? change.pin() : storedChecked(object);
    }

    private boolean storedChecked(FavoriteTreeNode object)
    {
        PinStore store = Activator.getDefault().getPinStore();
        return object.mdObject == null ? store.isObjectPinned(currentProject, object.target.uuid())
            : store.isObjectEffectivelyPinned(currentProject, object.uuidPath);
    }

    private final Map<FavoriteTreeNode, CheckSummary> checkSummaries = new IdentityHashMap<>();

    private record CheckSummary(int total, int checked)
    {
    }


    private CheckSummary checkSummary(FavoriteTreeNode node)
    {
        CheckSummary cached = checkSummaries.get(node);
        if (cached != null)
        {
            return cached;
        }
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

    private void invalidateViewCaches()
    {
        visibilityFilter.invalidate();
        checkSummaries.clear();
    }


    private void recordChange(FavoriteTreeNode object, boolean checked)
    {
        boolean originallyPinned = storedChecked(object);
        Map<String, PendingChange> projectPending = pendingByProject.get(currentProject);
        if (checked == originallyPinned)
        {
            if (projectPending != null)
            {
                projectPending.remove(object.target.uuid());
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
            projectPending.put(object.target.uuid(), new PendingChange(object.target, checked));
        }
    }

    @Override
    protected void okPressed()
    {
        applyPendingChanges();
        super.okPressed();
    }


    private void applyPendingChanges()
    {
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


    private record TreeBuildResult(List<FavoriteTreeNode> roots, boolean configurationAvailable)
    {
    }


    private static TreeBuildResult buildTree(String projectName)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        Configuration configuration = MetadataPinSupport.getConfiguration(project);
        if (configuration == null)
        {
            return new TreeBuildResult(List.of(), false);
        }

        FavoriteTreeModel.BuildResult buildResult = FavoriteTreeModel.build(configuration);

        Activator.getDefault().getPinStore().pruneMissingObjects(projectName, buildResult.existingUuids());

        return new TreeBuildResult(buildResult.roots(), true);
    }

    private static final class FavoriteTreeContentProvider implements ITreeContentProvider
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
            return null;
        }

        @Override
        public boolean hasChildren(Object element)
        {
            return element instanceof FavoriteTreeNode node && !node.children.isEmpty();
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
            cell.setStyleRanges(highlightRanges(node.normalizedLabel, visibilityFilter.pattern()));
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
            return mdObject == null ? null : navigatorLabelProvider.getImage(mdObject);
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

        private final Map<FavoriteTreeNode, Boolean> subtreeMatches = new IdentityHashMap<>();

        private final Map<FavoriteTreeNode, Boolean> labelSubtreeMatches = new IdentityHashMap<>();

        void setPattern(String text)
        {
            pattern = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            invalidate();
        }

        void setOnlySelected(boolean value)
        {
            onlySelected = value;
            invalidate();
        }

        boolean isOnlySelected()
        {
            return onlySelected;
        }

        String pattern()
        {
            return pattern;
        }

        void invalidate()
        {
            subtreeMatches.clear();
            labelSubtreeMatches.clear();
        }

        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element)
        {
            FavoriteTreeNode node = (FavoriteTreeNode)element;
            return matchesSubtree(node);
        }

        boolean matchesSubtree(FavoriteTreeNode node)
        {
            Boolean cached = subtreeMatches.get(node);
            if (cached != null)
            {
                return cached;
            }
            boolean result = objectMatches(node)
                || node.children.stream().anyMatch(this::matchesSubtree);
            subtreeMatches.put(node, result);
            return result;
        }


        boolean labelMatches(FavoriteTreeNode object)
        {
            return object.isObject()
                && !pattern.isEmpty()
                && object.normalizedLabel.contains(pattern)
                && (!onlySelected || effectiveChecked(object));
        }

        boolean labelMatchesSubtree(FavoriteTreeNode node)
        {
            Boolean cached = labelSubtreeMatches.get(node);
            if (cached != null)
            {
                return cached;
            }
            boolean result = labelMatches(node)
                || node.children.stream().anyMatch(this::labelMatchesSubtree);
            labelSubtreeMatches.put(node, result);
            return result;
        }

        private boolean objectMatches(FavoriteTreeNode object)
        {
            if (!object.isObject())
            {
                return false;
            }
            boolean textMatches = pattern.isEmpty()
                || object.normalizedLabel.contains(pattern)
                || object.normalizedFqn.contains(pattern);
            if (!textMatches)
            {
                return false;
            }
            return !onlySelected || effectiveChecked(object);
        }
    }
}
