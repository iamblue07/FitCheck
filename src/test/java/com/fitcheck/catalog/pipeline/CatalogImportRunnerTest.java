package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.service.CatalogImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogImportRunnerTest {

    @Mock
    private CatalogImportProperties properties;

    @Mock
    private CatalogImportService catalogImportService;

    @InjectMocks
    private CatalogImportRunner catalogImportRunner;

    @Mock
    private Random random;

    @Test
    void run_doesNothing_whenDisabled() {
        when(properties.enabled()).thenReturn(false);

        catalogImportRunner.run();

        verifyNoInteractions(catalogImportService);
    }

    @Test
    void run_runsImport_whenEnabledWithValidPaths() {
        when(properties.enabled()).thenReturn(true);
        when(properties.stylesCsvPath()).thenReturn("/data/styles.csv");
        when(properties.imagesCsvPath()).thenReturn("/data/images.csv");

        catalogImportRunner.run();

        verify(catalogImportService).importCatalog(Path.of("/data/styles.csv"), Path.of("/data/images.csv"));
    }

    @Test
    void run_doesNothing_whenStylesPathBlank() {
        when(properties.enabled()).thenReturn(true);
        when(properties.stylesCsvPath()).thenReturn("");

        catalogImportRunner.run();

        verify(catalogImportService, never()).importCatalog(any(), any());
    }

    @Test
    void run_doesNothing_whenImagesPathBlank() {
        when(properties.enabled()).thenReturn(true);
        when(properties.stylesCsvPath()).thenReturn("/data/styles.csv");
        when(properties.imagesCsvPath()).thenReturn("");

        catalogImportRunner.run();

        verify(catalogImportService, never()).importCatalog(any(), any());
    }
}