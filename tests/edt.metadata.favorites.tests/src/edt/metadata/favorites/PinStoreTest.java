/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PinStoreTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripPreservesProjectsObjectsAndEscapedLabels() throws Exception
    {
        PinStore store = newStore();
        PinTarget target = new PinTarget("uuid-1", "Catalog.\"Тест\\Путь\"");

        store.pinProject("SM");
        store.pinObject("SM", target);

        PinStore loaded = newStore();
        assertTrue(loaded.isProjectPinned("SM"));
        assertTrue(loaded.isObjectPinned("SM", "uuid-1"));
        assertEquals(List.of(target), loaded.getPinnedObjects("SM"));
        assertTrue(Files.readString(storageFile(), StandardCharsets.UTF_8).startsWith("formatVersion: 2"));
    }

    @Test
    public void pinningSameUuidAgainPersistsUpdatedReadableLabel() throws Exception
    {
        PinStore store = newStore();
        store.pinObject("SM", new PinTarget("uuid-1", "Catalog.СтароеИмя"));
        store.pinObject("SM", new PinTarget("uuid-1", "Catalog.НовоеИмя"));

        assertEquals(List.of(new PinTarget("uuid-1", "Catalog.НовоеИмя")),
            newStore().getPinnedObjects("SM"));
    }

    @Test
    public void bulkOperationsAreIsolatedByProject() throws Exception
    {
        PinStore store = newStore();
        PinTarget first = new PinTarget("uuid-1", "Catalog.Первый");
        PinTarget second = new PinTarget("uuid-2", "Catalog.Второй");
        store.pinObjects("SM", List.of(first, second));
        store.pinObject("OTHER", first);

        store.unpinObjects("SM", List.of(first));

        assertFalse(store.isObjectPinned("SM", "uuid-1"));
        assertTrue(store.isObjectPinned("SM", "uuid-2"));
        assertTrue(store.isObjectPinned("OTHER", "uuid-1"));
        assertEquals(List.of(second), store.getPinnedObjects("SM"));
    }

    @Test
    public void recursivePinIncludesFutureDescendantsAndPersists() throws Exception
    {
        PinTarget root = new PinTarget("root", "Catalog.Товары");
        PinTarget form = new PinTarget("form", "Catalog.Товары.Form.Основная");
        PinStore store = newStore();

        store.pinBranch("SM", root, List.of(root, form));

        PinStore loaded = newStore();
        assertTrue(loaded.isRecursivePin("SM", "root"));
        assertTrue(loaded.isObjectEffectivelyPinned("SM", List.of("root")));
        assertTrue(loaded.isObjectEffectivelyPinned("SM", List.of("form", "root")));
        assertTrue(loaded.isObjectEffectivelyPinned("SM", List.of("new-form", "root")));
    }

    @Test
    public void projectPinDoesNotActAsRecursiveObjectPin() throws Exception
    {
        PinStore store = newStore();
        store.pinProject("SM");

        assertFalse(store.isObjectEffectivelyPinned("SM", List.of("new-catalog")));

        store.pinObject("SM", new PinTarget("selected", "Catalog.Выбранный"));
        assertTrue(store.hasPinnedObjects("SM"));
        assertFalse(store.isObjectEffectivelyPinned("SM", List.of("new-catalog")));
    }

    @Test
    public void individualExclusionOverridesRecursivePinAndCanBePinnedAgain() throws Exception
    {
        PinTarget root = new PinTarget("root", "Catalog.Товары");
        PinTarget form = new PinTarget("form", "Catalog.Товары.Form.Основная");
        PinStore store = newStore();
        store.pinBranch("SM", root, List.of(root, form));

        store.unpinObject("SM", "form");
        assertFalse(store.isObjectEffectivelyPinned("SM", List.of("form", "root")));

        store.pinObject("SM", form);
        assertTrue(store.isObjectEffectivelyPinned("SM", List.of("form", "root")));
    }

    @Test
    public void ordinaryUnpinDoesNotBlockFutureRecursiveInheritance() throws Exception
    {
        PinTarget root = new PinTarget("root", "Catalog.Товары");
        PinTarget form = new PinTarget("form", "Catalog.Товары.Form.Основная");
        PinStore store = newStore();

        store.pinObject("SM", form);
        store.unpinObject("SM", "form");
        store.pinBranch("SM", root, List.of(root));

        assertTrue(store.isObjectEffectivelyPinned("SM", List.of("form", "root")));
    }

    @Test
    public void unpinnedBranchExcludesItsFutureDescendantsFromPinnedAncestor() throws Exception
    {
        PinTarget root = new PinTarget("root", "Catalog.Товары");
        PinTarget section = new PinTarget("section", "Catalog.Товары.TabularSection.Товары");
        PinStore store = newStore();
        store.pinBranch("SM", root, List.of(root, section));

        store.unpinBranch("SM", section, List.of(section));

        assertFalse(store.isObjectEffectivelyPinned("SM", List.of("section", "root")));
        assertFalse(store.isObjectEffectivelyPinned("SM", List.of("new-attribute", "section", "root")));
        assertTrue(store.isObjectEffectivelyPinned("SM", List.of("new-form", "root")));
    }

    @Test
    public void pruneMissingProjectsRemovesAllRelatedState() throws Exception
    {
        PinStore store = newStore();
        store.pinProject("SM");
        store.pinObject("SM", new PinTarget("uuid-sm", "Catalog.SM"));
        store.pinProject("REMOVED");
        store.pinObject("REMOVED", new PinTarget("uuid-removed", "Catalog.Removed"));

        store.pruneMissingProjects(Set.of("SM"));

        PinStore loaded = newStore();
        assertEquals(Set.of("SM"), loaded.getPinnedProjects());
        assertEquals(List.of(new PinTarget("uuid-sm", "Catalog.SM")), loaded.getPinnedObjects("SM"));
        assertTrue(loaded.getPinnedObjects("REMOVED").isEmpty());
    }

    @Test
    public void pruneMissingObjectsRemovesAllObjectStateAndKeepsExistingObjects() throws Exception
    {
        PinTarget root = new PinTarget("root", "Catalog.Товары");
        PinTarget existing = new PinTarget("existing", "Catalog.Товары.Form.Существующая");
        PinTarget missing = new PinTarget("missing", "Catalog.Товары.Form.Удалённая");
        PinTarget missingRoot = new PinTarget("missing-root", "Document.Удалённый");
        PinStore store = newStore();
        store.pinBranch("SM", root, List.of(root, existing, missing));
        store.pinBranch("SM", missingRoot, List.of(missingRoot));
        store.unpinObject("SM", "missing-exclusion");
        store.pinObject("OTHER", new PinTarget("other-missing", "Catalog.Другой"));

        store.pruneMissingObjects("SM", Set.of("root", "existing"));

        PinStore loaded = newStore();
        assertEquals(List.of(root, existing), loaded.getPinnedObjects("SM"));
        assertEquals(List.of(new PinTarget("other-missing", "Catalog.Другой")),
            loaded.getPinnedObjects("OTHER"));
        assertTrue(loaded.isRecursivePin("SM", "root"));
        assertFalse(loaded.isRecursivePin("SM", "missing-root"));
        assertTrue(loaded.isObjectEffectivelyPinned("SM", List.of("missing-exclusion", "root")));
    }

    @Test
    public void pruneLastMissingObjectMakesProjectObjectFree() throws Exception
    {
        PinStore store = newStore();
        store.pinObject("SM", new PinTarget("missing", "Catalog.Удалённый"));
        assertTrue(store.hasPinnedObjects("SM"));

        store.pruneMissingObjects("SM", Set.of());

        assertFalse(store.hasPinnedObjects("SM"));
    }

    @Test
    public void unpinAllObjectsKeepsProjectPin() throws Exception
    {
        PinStore store = newStore();
        store.pinProject("SM");
        store.pinObjects("SM", List.of(
            new PinTarget("uuid-1", "Catalog.Один"),
            new PinTarget("uuid-2", "Catalog.Два")));

        store.unpinAllObjects("SM");

        PinStore loaded = newStore();
        assertTrue(loaded.isProjectPinned("SM"));
        assertTrue(loaded.getPinnedObjects("SM").isEmpty());
    }

    @Test
    public void legacyFileWithoutFormatVersionIsStillReadable() throws Exception
    {
        Files.writeString(storageFile(), """
            pinnedProjects:
              - "SM"
            pinnedObjects:
              - project: "SM"
                uuid: "legacy-uuid"
                fqn: "Catalog.Legacy"
            """, StandardCharsets.UTF_8);

        PinStore store = newStore();
        assertTrue(store.isProjectPinned("SM"));
        assertEquals(List.of(new PinTarget("legacy-uuid", "Catalog.Legacy")), store.getPinnedObjects("SM"));
    }

    @Test
    public void returnedCollectionsAreImmutableSnapshots() throws Exception
    {
        PinStore store = newStore();
        store.pinProject("SM");
        store.pinObject("SM", new PinTarget("uuid-1", "Catalog.Один"));

        Set<String> projects = store.getPinnedProjects();
        List<PinTarget> objects = store.getPinnedObjects("SM");
        assertThrows(UnsupportedOperationException.class, () -> projects.add("OTHER"));
        assertThrows(UnsupportedOperationException.class,
            () -> objects.add(new PinTarget("uuid-2", "Catalog.Два")));
    }

    @Test
    public void futureFormatIsNotReadOrOverwritten() throws Exception
    {
        String futureFile = """
            formatVersion: 999
            pinnedProjects:
              - "FUTURE"
            pinnedObjects:
            """;
        Files.writeString(storageFile(), futureFile, StandardCharsets.UTF_8);

        PinStore store = newStore();
        assertFalse(store.isProjectPinned("FUTURE"));
        store.pinProject("NEW");

        assertEquals(futureFile, Files.readString(storageFile(), StandardCharsets.UTF_8));
    }

    private PinStore newStore() throws Exception
    {
        return new PinStore(new org.eclipse.core.runtime.Path(temporaryFolder.getRoot().getAbsolutePath()));
    }

    private Path storageFile() throws Exception
    {
        return temporaryFolder.getRoot().toPath().resolve("pinned.yaml");
    }
}
