package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.util.SecurityUtils;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationUpdatePackagePersistenceTest {
    ApplicationMapper apps=mock(ApplicationMapper.class);
    MinioObjectService objects=mock(MinioObjectService.class);
    MinioProperties properties=new MinioProperties();
    ApplicationServiceImpl service=new ApplicationServiceImpl(apps,mock(ApplicationLogMapper.class),null,mock(UserMapper.class),
            mock(SecurityUtils.class),null,null,objects,properties,null);
    String oldKey="app-updates/1/old.zip";
    MockMultipartFile file=new MockMultipartFile("file","new.zip","application/zip",new byte[]{1,2,3});
    @BeforeEach void setup(){
        var app=new Application();app.setId(1L);app.setOwnerId(7L);app.setUpdateFileStorageKey(oldKey);
        when(apps.selectById(1L)).thenReturn(app);when(apps.selectOne(any())).thenReturn(app);
        when(apps.update(isNull(),any())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clearSynchronization();}
    @Test void previousObjectSurvivesUntilDatabaseCommit() {
        service.uploadUpdatePackage(1L,file,7L);
        verify(objects,never()).deleteObject(anyString(),anyString());
        var sync=TransactionSynchronizationManager.getSynchronizations();
        sync.forEach(TransactionSynchronization::afterCommit);
        sync.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(objects).deleteObject(properties.getBucket(),oldKey);
        verify(objects,times(1)).deleteObject(anyString(),anyString());
    }
    @Test void databaseFailureKeepsOldDownloadAndCleansOnlyTheNewUpload() {
        when(apps.update(isNull(),any())).thenThrow(new IllegalStateException("synthetic-db-failure"));
        assertThrows(IllegalStateException.class,()->service.uploadUpdatePackage(1L,file,7L));
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(objects,never()).deleteObject(properties.getBucket(),oldKey);
        verify(objects,atLeastOnce()).deleteObject(eq(properties.getBucket()),argThat(key->key.startsWith("app-updates/1/")&&!key.equals(oldKey)));
    }
    @Test void laterTransactionRollbackKeepsPreviousObject() {
        service.uploadUpdatePackage(1L,file,7L);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(objects,never()).deleteObject(properties.getBucket(),oldKey);
        verify(objects,times(1)).deleteObject(eq(properties.getBucket()),argThat(key->!key.equals(oldKey)));
    }
}
