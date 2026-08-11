/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jface.viewers.TreeViewer;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class FavoritesManagementDialogStructureTest
{
    private static final Set<String> EDT_2026_1_CONFIGURATION_TYPES = Set.of(
        "Language", "Subsystem", "StyleItem", "Style", "CommonPicture", "Interface", "SessionParameter",
        "Role", "CommonTemplate", "FilterCriterion", "CommonModule", "CommonAttribute", "ExchangePlan",
        "XDTOPackage", "WebService", "HTTPService", "WSReference", "EventSubscription", "ScheduledJob",
        "SettingsStorage", "FunctionalOption", "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
        "CommandGroup", "Constant", "CommonForm", "Catalog", "Document", "DocumentNumerator", "Sequence",
        "DocumentJournal", "Enum", "Report", "DataProcessor", "InformationRegister", "AccumulationRegister",
        "ChartOfCharacteristicTypes", "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
        "CalculationRegister", "BusinessProcess", "Task", "ExternalDataSource", "IntegrationService", "Bot",
        "WebSocketClient", "PaletteColor");

    private static final Set<String> COMMON_TYPES = Set.of(
        "Subsystem", "CommonModule", "SessionParameter", "Role", "CommonAttribute", "ExchangePlan",
        "FilterCriterion", "EventSubscription", "ScheduledJob", "Bot", "FunctionalOption",
        "FunctionalOptionsParameter", "DefinedType", "SettingsStorage", "CommonForm", "CommonCommand",
        "CommandGroup", "CommonTemplate", "CommonPicture", "XDTOPackage", "WebService", "HTTPService",
        "WSReference", "WebSocketClient", "IntegrationService", "PaletteColor", "StyleItem", "Style",
        "Language", "Interface");

    private static final List<String> COMMON_LABEL_ORDER = List.of(
        "Подсистемы", "Общие модули", "Параметры сеанса", "Роли", "Общие реквизиты", "Планы обмена",
        "Критерии отбора", "Подписки на события", "Регламентные задания", "Боты", "Функциональные опции",
        "Параметры функциональных опций", "Определяемые типы", "Хранилища настроек", "Общие формы",
        "Общие команды", "Группы команд", "Общие макеты", "Общие картинки", "XDTO-пакеты", "Web-сервисы",
        "HTTP-сервисы", "WS-ссылки", "WebSocket-клиенты", "Сервисы интеграции", "Цвета палитры",
        "Элементы стиля", "Стили", "Языки", "Интерфейсы");

    @Test
    public void everyConfigurationTypeHasExplicitRussianNames()
    {
        Map<String, MetadataTypeNames.KindNames> names = MetadataTypeNames.kindNames();
        assertEquals(EDT_2026_1_CONFIGURATION_TYPES, names.keySet());

        Set<String> groupLabels = new HashSet<>();
        Set<String> fqnNames = new HashSet<>();
        names.forEach((kind, kindNames) -> {
            assertFalse(kind, kindNames.groupLabel().isBlank());
            assertFalse(kind, kindNames.fqnName().isBlank());
            assertTrue(kind, groupLabels.add(kindNames.groupLabel()));
            assertTrue(kind, fqnNames.add(kindNames.fqnName()));
        });
    }

    @Test
    public void navigatorGroupLabelComesFromTheSameNameTable()
    {
        assertEquals("Справочники", MetadataTypeNames.groupLabel("Catalog"));
        assertEquals("Регистры сведений", MetadataTypeNames.groupLabel("InformationRegister"));
        assertEquals("UnknownKind", MetadataTypeNames.groupLabel("UnknownKind"));
    }

    @Test
    public void russianTypeNameIsTranslatedToClassName()
    {
        assertEquals("catalog", MetadataTypeNames.kindForTypeName("справочник"));
        assertEquals("catalog", MetadataTypeNames.kindForTypeName("справочники"));
        assertEquals("informationregister", MetadataTypeNames.kindForTypeName("регистрсведений"));
        assertEquals("informationregister", MetadataTypeNames.kindForTypeName("регистры сведений"));
        assertEquals("calculationregister", MetadataTypeNames.kindForTypeName("регистррасчета"));
        assertEquals("report", MetadataTypeNames.kindForTypeName("отчёт"));
        assertEquals("report", MetadataTypeNames.kindForTypeName("отчет"));
    }

    @Test
    public void russianNestedTypeNameIsTranslatedToClassName()
    {
        assertEquals("form", MetadataTypeNames.kindForTypeName("форма"));
        assertEquals("form", MetadataTypeNames.kindForTypeName("формы"));
        assertEquals("attribute", MetadataTypeNames.kindForTypeName("реквизит"));
        assertEquals("tabularsection", MetadataTypeNames.kindForTypeName("табличная часть"));
        assertEquals("template", MetadataTypeNames.kindForTypeName("макет"));
        assertEquals("enumvalue", MetadataTypeNames.kindForTypeName("значения"));
        assertEquals("recalculation", MetadataTypeNames.kindForTypeName("перерасчет"));
        assertTrue(MetadataTypeNames.isNestedTypeName("форма"));
        assertFalse(MetadataTypeNames.isNestedTypeName("справочник"));
        assertFalse(MetadataTypeNames.isNestedTypeName("контр"));
    }

    @Test
    public void unknownSegmentIsNotTranslated()
    {
        assertNull(MetadataTypeNames.kindForTypeName(""));
        assertNull(MetadataTypeNames.kindForTypeName("контр"));
        assertNull(MetadataTypeNames.kindForTypeName("справочник.контр"));
    }

    @Test
    public void englishTypeNameIsRecognizedAsWell()
    {
        assertEquals("catalog", MetadataTypeNames.kindForTypeName("catalog"));
        assertEquals("form", MetadataTypeNames.kindForTypeName("form"));
        assertFalse(MetadataTypeNames.isNestedTypeName("catalog"));
        assertTrue(MetadataTypeNames.isNestedTypeName("form"));
    }

    @Test
    public void searchQueryIsNotRewrittenForAppliedState()
    {
        assertFalse(FavoritesManagementDialog.searchViewUpdating(
            "Справочник.Контр", "справочник.контр", true, false));
        assertTrue(FavoritesManagementDialog.searchViewUpdating(
            "Справочник.Контр", "catalog.контр", true, false));
    }

    @Test
    public void commonBranchContainsExpectedTypesInNavigatorOrder() throws Exception
    {
        Set<String> commonTypes = modelField("COMMON_KINDS");
        List<String> commonOrder = modelField("COMMON_GROUP_ORDER");

        assertEquals(COMMON_TYPES, commonTypes);
        assertEquals(COMMON_LABEL_ORDER, commonOrder);
        assertEquals(Set.copyOf(COMMON_LABEL_ORDER), commonTypes.stream()
            .map(MetadataTypeNames::groupLabel).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    public void nestedNavigatorGroupsHaveRussianLabels() throws Exception
    {
        Map<String, String> labels = modelField("NESTED_GROUP_LABELS");

        assertEquals("Реквизиты", labels.get("attributes"));
        assertEquals("Табличные части", labels.get("tabularSections"));
        assertEquals("Формы", labels.get("forms"));
        assertEquals("Команды", labels.get("commands"));
        assertEquals("Макеты", labels.get("templates"));
        assertEquals("Измерения", labels.get("dimensions"));
        assertEquals("Ресурсы", labels.get("resources"));
    }

    @Test
    public void searchUsesMinimumLengthAndBoundedAutoExpansion() throws Exception
    {
        assertEquals(Integer.valueOf(3), field("MIN_SEARCH_PATTERN_LENGTH"));
        assertEquals(Integer.valueOf(300),
            field(FavoriteUiLimits.class, "MAX_AUTO_EXPANDED_OBJECTS"));
    }

    @Test
    public void managementTreeDoesNotUseCheckboxViewer() throws Exception
    {
        assertEquals(TreeViewer.class,
            FavoritesManagementDialog.class.getDeclaredField("treeViewer").getType());
    }

    @Test
    public void favoriteColumnHasThreeSummaryStates()
    {
        assertEquals(FavoritesManagementDialog.FavoriteState.NONE,
            FavoritesManagementDialog.favoriteState(10, 0));
        assertEquals(FavoritesManagementDialog.FavoriteState.PARTIAL,
            FavoritesManagementDialog.favoriteState(10, 4));
        assertEquals(FavoritesManagementDialog.FavoriteState.ALL,
            FavoritesManagementDialog.favoriteState(10, 10));
    }

    @Test
    public void managementTreeUuidPathIncludesConfiguration()
    {
        FavoriteTreeNode group = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog =
            FavoriteTreeNode.object("Товары", new PinTarget("catalog", "Catalog.Товары"), null);
        FavoriteTreeNode forms = FavoriteTreeNode.group("Формы");
        FavoriteTreeNode form = FavoriteTreeNode.object("Основная",
            new PinTarget("form", "Catalog.Товары.Form.Основная"), null);
        group.addChild(catalog);
        catalog.addChild(forms);
        forms.addChild(form);

        assertEquals(List.of("form", "catalog", "configuration"),
            FavoritesManagementDialog.uuidPath(form, "configuration"));
    }

    @Test
    public void fqnSearchHighlightsLastSegmentInObjectLabel()
    {
        assertEquals("контр",
            FavoritesManagementDialog.searchHighlightPattern("catalogs.контр"));
        assertEquals("контр",
            FavoritesManagementDialog.searchHighlightPattern("контр"));
    }

    @Test
    public void bulkActionsWaitUntilChangedSearchIsApplied()
    {
        assertFalse(FavoritesManagementDialog.searchViewUpdating("ко", "", true, false));
        assertTrue(FavoritesManagementDialog.searchViewUpdating("контр", "", true, false));
        assertTrue(FavoritesManagementDialog.searchViewUpdating("", "контр", true, false));
        assertFalse(FavoritesManagementDialog.searchViewUpdating(
            "контр", "контр", true, false));
        assertTrue(FavoritesManagementDialog.searchViewUpdating("контр", "контр", false, true));
    }

    @Test
    public void selectAllIsDisabledForOnlySelectedMode()
    {
        assertEquals(new FavoritesManagementDialog.BulkActionState(true, true),
            FavoritesManagementDialog.bulkActionState(false, false));
        assertEquals(new FavoritesManagementDialog.BulkActionState(false, true),
            FavoritesManagementDialog.bulkActionState(false, true));
        assertEquals(new FavoritesManagementDialog.BulkActionState(false, false),
            FavoritesManagementDialog.bulkActionState(true, false));
        assertEquals(new FavoritesManagementDialog.BulkActionState(false, false),
            FavoritesManagementDialog.bulkActionState(true, true));
    }

    @Test
    public void searchStatusIsDerivedFromCurrentSearchState()
    {
        assertEquals("Поиск\u2026",
            FavoritesManagementDialog.searchStatusMessage("контр", "", 0, "", true));
        assertEquals("",
            FavoritesManagementDialog.searchStatusMessage("", "", 0, "", false));
        assertEquals("Введите не менее 3 символов для поиска.",
            FavoritesManagementDialog.searchStatusMessage("ко", "", 0, "", false));
        assertEquals("Найдено объектов: 7",
            FavoritesManagementDialog.searchStatusMessage("контр", "контр", 7, "", false));
        assertEquals("Конфигурация недоступна",
            FavoritesManagementDialog.searchStatusMessage(
                "", "", 0, "Конфигурация недоступна", false));
    }

    @Test
    public void editorCommandContributesToLastMainToolbarGroup() throws Exception
    {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(FavoritesManagementDialogStructureTest.class.getResourceAsStream("/plugin.xml"))
            .getDocumentElement();
        NodeList contributions = root.getElementsByTagName("menuContribution");

        boolean found = false;
        for (int i = 0; i < contributions.getLength(); i++)
        {
            Element contribution = (Element)contributions.item(i);
            if ("toolbar:org.eclipse.ui.main.toolbar?before=PerspectiveSpacer"
                .equals(contribution.getAttribute("locationURI"))
                && containsToolbarCommand(contribution, "edt.metadata.favorites.editorToolbar",
                    "edt.metadata.favorites.toggleEditorPin"))
            {
                found = true;
                break;
            }
        }

        assertTrue("Editor pin command must be in the last main toolbar group", found);
    }

    @Test
    public void gitChangedFilterFollowsFavoriteFilterOnNavigatorToolbar() throws Exception
    {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(FavoritesManagementDialogStructureTest.class.getResourceAsStream("/plugin.xml"))
            .getDocumentElement();
        NodeList contributions = root.getElementsByTagName("menuContribution");

        for (int i = 0; i < contributions.getLength(); i++)
        {
            Element contribution = (Element)contributions.item(i);
            if (!"toolbar:com._1c.g5.v8.dt.ui2.navigator"
                .equals(contribution.getAttribute("locationURI")))
            {
                continue;
            }

            NodeList commands = contribution.getElementsByTagName("command");
            int favoriteIndex = commandIndex(commands, "edt.metadata.favorites.toggleFilter");
            int gitIndex = commandIndex(commands, "edt.metadata.favorites.toggleGitChangedFilter");
            assertTrue(favoriteIndex >= 0);
            assertEquals(favoriteIndex + 1, gitIndex);
            assertEquals("icons/git-changed.png",
                ((Element)commands.item(gitIndex)).getAttribute("icon"));
            return;
        }

        throw new AssertionError("Navigator toolbar contribution is missing");
    }

    private static int commandIndex(NodeList commands, String commandId)
    {
        for (int i = 0; i < commands.getLength(); i++)
        {
            if (commandId.equals(((Element)commands.item(i)).getAttribute("commandId")))
            {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void allDtEditorsUseCommonTypeAndNavigatorShortcut() throws Exception
    {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(FavoritesManagementDialogStructureTest.class.getResourceAsStream("/plugin.xml"))
            .getDocumentElement();
        NodeList typeChecks = root.getElementsByTagName("instanceof");
        int commonEditorChecks = 0;
        for (int i = 0; i < typeChecks.getLength(); i++)
        {
            Element check = (Element)typeChecks.item(i);
            if ("com._1c.g5.v8.dt.ui.editor.IDtEditor".equals(check.getAttribute("value")))
            {
                commonEditorChecks++;
            }
        }

        assertEquals(3, commonEditorChecks);
        assertTrue("Ctrl+Alt+K must invoke the shared navigator/editor command",
            containsKeyBinding(root, "M1+M3+K", "edt.metadata.favorites.togglePin"));
    }

    private static boolean containsKeyBinding(Element root, String sequence, String commandId)
    {
        NodeList keys = root.getElementsByTagName("key");
        for (int i = 0; i < keys.getLength(); i++)
        {
            Element key = (Element)keys.item(i);
            if (sequence.equals(key.getAttribute("sequence"))
                && commandId.equals(key.getAttribute("commandId")))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsToolbarCommand(Element contribution, String toolbarId, String commandId)
    {
        NodeList toolbars = contribution.getElementsByTagName("toolbar");
        for (int i = 0; i < toolbars.getLength(); i++)
        {
            Element toolbar = (Element)toolbars.item(i);
            if (toolbarId.equals(toolbar.getAttribute("id")) && containsCommand(toolbar, commandId))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCommand(Element toolbar, String commandId)
    {
        NodeList commands = toolbar.getElementsByTagName("command");
        for (int i = 0; i < commands.getLength(); i++)
        {
            if (commandId.equals(((Element)commands.item(i)).getAttribute("commandId")))
            {
                return true;
            }
        }
        return false;
    }

    private static <T> T field(String name) throws Exception
    {
        return field(FavoritesManagementDialog.class, name);
    }


    private static <T> T modelField(String name) throws Exception
    {
        return field(FavoriteTreeModel.class, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> owner, String name) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T)field.get(null);
    }
}
