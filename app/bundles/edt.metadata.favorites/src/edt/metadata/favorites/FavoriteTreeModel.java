/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdClassPackage;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;


final class FavoriteTreeModel
{

    private static final Map<String, String> GROUP_LABELS = Map.ofEntries(
        Map.entry("Catalog", "Справочники"),
        Map.entry("Document", "Документы"),
        Map.entry("Enum", "Перечисления"),
        Map.entry("Report", "Отчёты"),
        Map.entry("DataProcessor", "Обработки"),
        Map.entry("CommonModule", "Общие модули"),
        Map.entry("Role", "Роли"),
        Map.entry("Constant", "Константы"),
        Map.entry("Language", "Языки"),
        Map.entry("StyleItem", "Элементы стиля"),
        Map.entry("Style", "Стили"),
        Map.entry("PaletteColor", "Цвета палитры"),
        Map.entry("Interface", "Интерфейсы"),
        Map.entry("CommonPicture", "Общие картинки"),
        Map.entry("CommonTemplate", "Общие макеты"),
        Map.entry("CommonAttribute", "Общие реквизиты"),
        Map.entry("XDTOPackage", "XDTO-пакеты"),
        Map.entry("WSReference", "WS-ссылки"),
        Map.entry("WebSocketClient", "WebSocket-клиенты"),
        Map.entry("EventSubscription", "Подписки на события"),
        Map.entry("ScheduledJob", "Регламентные задания"),
        Map.entry("Bot", "Боты"),
        Map.entry("SettingsStorage", "Хранилища настроек"),
        Map.entry("FunctionalOptionsParameter", "Параметры функциональных опций"),
        Map.entry("DefinedType", "Определяемые типы"),
        Map.entry("CommandGroup", "Группы команд"),
        Map.entry("FilterCriterion", "Критерии отбора"),
        Map.entry("IntegrationService", "Сервисы интеграции"),
        Map.entry("InformationRegister", "Регистры сведений"),
        Map.entry("AccumulationRegister", "Регистры накопления"),
        Map.entry("AccountingRegister", "Регистры бухгалтерии"),
        Map.entry("CalculationRegister", "Регистры расчёта"),
        Map.entry("ChartOfCharacteristicTypes", "Планы видов характеристик"),
        Map.entry("ChartOfAccounts", "Планы счетов"),
        Map.entry("ChartOfCalculationTypes", "Планы видов расчёта"),
        Map.entry("DocumentNumerator", "Нумераторы документов"),
        Map.entry("DocumentJournal", "Журналы документов"),
        Map.entry("Sequence", "Последовательности"),
        Map.entry("ExternalDataSource", "Внешние источники данных"),
        Map.entry("ExchangePlan", "Планы обмена"),
        Map.entry("BusinessProcess", "Бизнес-процессы"),
        Map.entry("Task", "Задачи"),
        Map.entry("CommonForm", "Общие формы"),
        Map.entry("CommonCommand", "Общие команды"),
        Map.entry("SessionParameter", "Параметры сеанса"),
        Map.entry("FunctionalOption", "Функциональные опции"),
        Map.entry("WebService", "Web-сервисы"),
        Map.entry("HTTPService", "HTTP-сервисы"),
        Map.entry("Subsystem", "Подсистемы"));


    private static final Map<String, String> NESTED_GROUP_LABELS = Map.ofEntries(
        Map.entry("attributes", "Реквизиты"),
        Map.entry("tabularSections", "Табличные части"),
        Map.entry("forms", "Формы"),
        Map.entry("commands", "Команды"),
        Map.entry("templates", "Макеты"),
        Map.entry("dimensions", "Измерения"),
        Map.entry("resources", "Ресурсы"),
        Map.entry("recalculations", "Перерасчёты"),
        Map.entry("enumValues", "Значения"),
        Map.entry("columns", "Графы"),
        Map.entry("addressingAttributes", "Реквизиты адресации"),
        Map.entry("operations", "Операции"),
        Map.entry("fields", "Поля"),
        Map.entry("parameters", "Параметры"));

    static final String COMMON_GROUP = "Общие";


    private static final Set<String> COMMON_KINDS = Set.of(
        "Subsystem", "CommonModule", "SessionParameter", "Role", "CommonAttribute", "ExchangePlan",
        "FilterCriterion", "EventSubscription", "ScheduledJob", "Bot", "FunctionalOption",
        "FunctionalOptionsParameter", "DefinedType", "SettingsStorage", "CommonForm", "CommonCommand",
        "CommandGroup", "CommonTemplate", "CommonPicture", "XDTOPackage", "WebService", "HTTPService",
        "WSReference", "WebSocketClient", "IntegrationService", "PaletteColor", "StyleItem", "Style",
        "Language", "Interface");


    private static final List<String> COMMON_GROUP_ORDER = List.of(
        "Подсистемы", "Общие модули", "Параметры сеанса", "Роли", "Общие реквизиты", "Планы обмена",
        "Критерии отбора", "Подписки на события", "Регламентные задания", "Боты", "Функциональные опции",
        "Параметры функциональных опций", "Определяемые типы", "Хранилища настроек", "Общие формы",
        "Общие команды", "Группы команд", "Общие макеты", "Общие картинки", "XDTO-пакеты", "Web-сервисы",
        "HTTP-сервисы", "WS-ссылки", "WebSocket-клиенты", "Сервисы интеграции", "Цвета палитры",
        "Элементы стиля", "Стили", "Языки", "Интерфейсы");

    private FavoriteTreeModel()
    {
    }


    record BuildResult(List<FavoriteTreeNode> roots, Set<String> existingUuids)
    {
    }


    static BuildResult build(Configuration configuration)
    {
        Map<String, MdObject> objectsByUuid = new LinkedHashMap<>();
        for (EReference reference : configuration.eClass().getEAllReferences())
        {
            if (!reference.isMany()
                || !MdClassPackage.Literals.MD_OBJECT.isSuperTypeOf(reference.getEReferenceType()))
            {
                continue;
            }

            Object value = configuration.eGet(reference, true);
            if (!(value instanceof Iterable<?> values))
            {
                continue;
            }
            for (Object candidate : values)
            {
                if (candidate instanceof EObject eObject)
                {
                    EObject resolved = EcoreUtil.resolve(eObject, configuration);
                    if (!(resolved instanceof MdObject mdObject))
                    {
                        continue;
                    }
                    String uuid = MetadataPinSupport.getUuid(mdObject);
                    if (uuid != null)
                    {
                        objectsByUuid.putIfAbsent(uuid, mdObject);
                    }
                }
            }
        }

        Map<String, FavoriteTreeNode> roots = new LinkedHashMap<>();
        Map<String, FavoriteTreeNode> commonGroups = new LinkedHashMap<>();
        Set<String> existingUuids = new LinkedHashSet<>();
        for (MdObject mdObject : objectsByUuid.values())
        {
            String uuid = MetadataPinSupport.getUuid(mdObject);
            String kind = mdObject.eClass().getName();
            String groupLabel = GROUP_LABELS.getOrDefault(kind, kind);
            FavoriteTreeNode group;
            if (COMMON_KINDS.contains(kind))
            {
                FavoriteTreeNode commonRoot = roots.computeIfAbsent(COMMON_GROUP, FavoriteTreeNode::group);
                group = commonGroups.computeIfAbsent(groupLabel, label -> {
                    FavoriteTreeNode child = FavoriteTreeNode.group(label);
                    commonRoot.children.add(child);
                    return child;
                });
            }
            else
            {
                group = roots.computeIfAbsent(groupLabel, FavoriteTreeNode::group);
            }
            String fqn = MetadataPinSupport.getFqn(mdObject);
            String label = mdObject.getName() != null ? mdObject.getName() : fqn;
            FavoriteTreeNode objectNode = FavoriteTreeNode.object(label, new PinTarget(uuid, fqn), mdObject);
            group.children.add(objectNode);
            appendNestedObjects(objectNode, mdObject, existingUuids);
        }

        List<FavoriteTreeNode> result = new ArrayList<>(roots.values());
        for (FavoriteTreeNode root : result)
        {
            if (COMMON_GROUP.equals(root.label))
            {
                root.children.sort(Comparator.comparingInt(
                    node -> commonGroupOrder(node.label)));
            }
            root.sortLeafChildren();
        }
        return new BuildResult(result, existingUuids);
    }


    private static void appendNestedObjects(FavoriteTreeNode rootNode, MdObject rootObject,
        Set<String> existingUuids)
    {
        Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups = new IdentityHashMap<>();
        Set<EObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        existingUuids.add(rootNode.target.uuid());
        appendContainedObjects(rootObject, rootNode, null, groups, existingUuids, visited);
    }

    private static void appendContainedObjects(EObject owner, FavoriteTreeNode parentNode,
        String inheritedGroupKey, Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups,
        Set<String> existingUuids, Set<EObject> visited)
    {
        if (!visited.add(owner))
        {
            return;
        }
        for (EReference reference : owner.eClass().getEAllContainments())
        {
            Object value = owner.eGet(reference, true);
            if (reference.isMany() && value instanceof Iterable<?> values)
            {
                for (Object candidate : values)
                {
                    if (candidate instanceof EObject child)
                    {
                        appendContainedObject(EcoreUtil.resolve(child, owner), parentNode,
                            inheritedGroupKey == null ? reference.getName() : inheritedGroupKey,
                            groups, existingUuids, visited);
                    }
                }
            }
            else if (value instanceof EObject child)
            {
                appendContainedObject(EcoreUtil.resolve(child, owner), parentNode,
                    inheritedGroupKey == null ? reference.getName() : inheritedGroupKey,
                    groups, existingUuids, visited);
            }
        }
    }

    private static void appendContainedObject(EObject child, FavoriteTreeNode parentNode, String groupKey,
        Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups, Set<String> existingUuids,
        Set<EObject> visited)
    {
        if (child instanceof MdObject mdObject)
        {
            String uuid = MetadataPinSupport.getUuid(mdObject);
            if (uuid == null)
            {
                appendContainedObjects(child, parentNode, groupKey, groups, existingUuids, visited);
                return;
            }
            String groupLabel = NESTED_GROUP_LABELS.getOrDefault(groupKey, groupKey);
            FavoriteTreeNode nestedGroup = groups.computeIfAbsent(parentNode, key -> new LinkedHashMap<>())
                .computeIfAbsent(groupKey, key -> {
                    FavoriteTreeNode value = FavoriteTreeNode.group(groupLabel);
                    parentNode.children.add(value);
                    return value;
                });

            String fqn = MetadataPinSupport.getFqn(mdObject);
            String label = mdObject.getName() != null ? mdObject.getName() : fqn;
            FavoriteTreeNode childNode = FavoriteTreeNode.object(label, new PinTarget(uuid, fqn), mdObject);
            nestedGroup.children.add(childNode);
            existingUuids.add(uuid);
            appendContainedObjects(child, childNode, null, groups, existingUuids, visited);
        }
        else
        {
            appendContainedObjects(child, parentNode, groupKey, groups, existingUuids, visited);
        }
    }

    private static int commonGroupOrder(String label)
    {
        int index = COMMON_GROUP_ORDER.indexOf(label);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
