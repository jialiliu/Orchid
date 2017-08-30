package com.eden.orchid.api.services;

import com.caseyjbrooks.clog.Clog;
import com.eden.orchid.api.OrchidContext;
import com.eden.orchid.api.OrchidService;
import com.eden.orchid.api.resources.ResourceService;
import com.eden.orchid.api.resources.ResourceServiceImpl;
import com.eden.orchid.api.resources.resourceSource.DefaultResourceSource;
import com.eden.orchid.api.resources.resourceSource.LocalResourceSource;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

public final class ResourceServiceTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Clog.setMinPriority(Clog.Priority.FATAL);
    }

    private OrchidContext context;
    private ResourceService underTest;
    private ResourceServiceImpl service;

    private String resourcesDir;
    private Set<LocalResourceSource> localResourceSources;
    private LocalResourceSource mockLocalResourceSource;
    private Set<DefaultResourceSource> defaultResourceSources;
    private DefaultResourceSource mockDefaultResourceSource;

    @Before
    public void testSetup() {
        resourcesDir = "mockResourcesDir";
        localResourceSources = new HashSet<>();
        mockLocalResourceSource = mock(LocalResourceSource.class);
        localResourceSources.add(mockLocalResourceSource);

        defaultResourceSources = new HashSet<>();
        mockDefaultResourceSource = mock(DefaultResourceSource.class);
        defaultResourceSources.add(mockDefaultResourceSource);

        // test the service directly
        context = mock(OrchidContext.class);
        service = new ResourceServiceImpl(resourcesDir, localResourceSources, defaultResourceSources);
        service.initialize(context);

        // test that the public implementation is identical to the real implementation
        underTest = new ResourceService() {
            public void initialize(OrchidContext context) { }
            public <T extends OrchidService> T getService(Class<T> serviceClass) { return (T) service; }
        };
    }

}