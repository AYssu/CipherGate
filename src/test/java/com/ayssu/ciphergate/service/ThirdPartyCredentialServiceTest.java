package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.ThirdPartyCredentialDTO;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.impl.ThirdPartyCredentialServiceImpl;
import com.ayssu.ciphergate.util.SecurityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThirdPartyCredentialServiceTest {
    ThirdPartyCredentialMapper credentials;
    ThirdPartyCredentialAppMapper bindings;
    ApplicationMapper apps;
    SecurityUtils security;
    ThirdPartyCredentialServiceImpl service;
    ThirdPartyCredential row;

    @BeforeAll static void metadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "unit-test"), ThirdPartyCredential.class);
    }

    @BeforeEach void setup() {
        credentials = mock(ThirdPartyCredentialMapper.class);
        bindings = mock(ThirdPartyCredentialAppMapper.class);
        apps = mock(ApplicationMapper.class);
        security = mock(SecurityUtils.class);
        service = new ThirdPartyCredentialServiceImpl(credentials, bindings,
                mock(ThirdPartyRechargeLogMapper.class), apps, security);
        row = new ThirdPartyCredential(); row.setId(5L); row.setAppId(10L); row.setDeleted(0);
        row.setName("original"); row.setApiKey("test_key"); row.setApiSecret("test_secret");
        row.setUsedCallCount(9L); row.setUsedDaysCount(63L);
        when(credentials.selectById(5L)).thenReturn(row);
        when(credentials.selectForUpdate(5L)).thenReturn(row);
        when(bindings.selectAppIds(5L)).thenReturn(List.of(10L, 20L));
        for (long id : List.of(10L, 20L)) {
            Application app = new Application(); app.setId(id); app.setOwnerId(1L); app.setDeleted(0);
            when(apps.selectById(id)).thenReturn(app);
        }
    }

    ThirdPartyCredentialDTO dto(List<Long> ids) {
        var dto = new ThirdPartyCredentialDTO(); dto.setName("Test recharge key"); dto.setAppIds(ids); return dto;
    }

    @Test void newPermissionsDefaultClosedAndRequireAdministratorAndIp() {
        var input=dto(List.of(10L)); input.setAllowQuery(true);
        assertThrows(IllegalArgumentException.class,()->service.createCredential(input,1L));
        when(security.isAdmin(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.createCredential(input,1L));
        input.setAllowedIps("0.0.0.0/0");
        assertThrows(IllegalArgumentException.class,()->service.createCredential(input,1L));
        input.setAllowedIps("192.0.2.1,2001:db8::1");
        var created=service.createCredential(input,1L);
        assertTrue(created.getAllowQuery()); assertFalse(created.getAllowTrial());
    }
    @Test void privilegedCredentialCannotBeModifiedRotatedOrDeletedByOwner() {
        row.setAllowQuery(true);
        assertThrows(IllegalArgumentException.class,()->service.updateCredential(5L,dto(List.of(10L)),1L));
        assertThrows(IllegalArgumentException.class,()->service.rotateSecret(5L,1L));
        assertThrows(IllegalArgumentException.class,()->service.deleteCredential(5L,1L));
        verify(credentials,never()).update(any(),any()); verify(credentials,never()).deleteById(anyLong());
    }
    @Test void administratorCanExplicitlyRevokePermissions() {
        row.setAllowQuery(true); row.setAllowTrial(true); when(security.isAdmin(1L)).thenReturn(true);
        var input=dto(List.of(10L));input.setAllowQuery(false); input.setAllowTrial(false);
        var updated=service.updateCredential(5L,input,1L);
        assertFalse(updated.getAllowQuery());assertFalse(updated.getAllowTrial());assertNull(updated.getApiSecret());
    }

    @Test void createDeduplicatesBindingsAndReturnsSecretOnlyOnce() {
        doAnswer(inv -> { ((ThirdPartyCredential)inv.getArgument(0)).setId(5L); return 1; })
                .when(credentials).insert(any(ThirdPartyCredential.class));
        var created = service.createCredential(dto(List.of(10L, 20L, 10L)), 1L);
        assertEquals(List.of(10L, 20L), created.getAppIds());
        assertTrue(created.getApiKey().startsWith("tpk_")); assertTrue(created.getApiSecret().startsWith("tps_"));
        verify(bindings).insertBindings(5L, List.of(10L, 20L));
        assertEquals(0L, created.getUsedCallCount());
    }

    @Test void legacySingleAppInputStillWorks() {
        var dto = dto(null); dto.setAppId(10L);
        var created = service.createCredential(dto, 1L);
        assertEquals(List.of(10L), created.getAppIds());
    }

    static Stream<List<Long>> invalidIds() {
        return Stream.of(List.of(), Arrays.asList(10L, null), List.of(0L), List.of(-1L),
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList());
    }
    @ParameterizedTest @MethodSource("invalidIds")
    void invalidBindingSetsFailBeforeDatabaseWrites(List<Long> ids) {
        assertThrows(RuntimeException.class, () -> service.createCredential(dto(ids), 1L));
        verifyNoInteractions(credentials, bindings);
    }

    @Test void noBindingsOnCreateIsRejected() {
        assertThrows(RuntimeException.class, () -> service.createCredential(dto(null), 1L));
    }
    @Test void contradictoryLegacyAndMultiAppInputsAreRejected() {
        var dto = dto(List.of(20L)); dto.setAppId(10L);
        assertThrows(RuntimeException.class, () -> service.createCredential(dto, 1L));
        verifyNoInteractions(credentials, bindings);
    }
    @Test void missingAppCannotBeBoundEvenByAdministrator() {
        when(security.isAdmin(1L)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.createCredential(dto(List.of(30L)), 1L));
        verifyNoInteractions(credentials, bindings);
    }
    @Test void cannotCreateCredentialForAnotherOwnersApp() {
        assertThrows(RuntimeException.class, () -> service.createCredential(dto(List.of(10L)), 2L));
        verifyNoInteractions(credentials, bindings);
    }
    @Test void detailHidesSecretAndReturnsAllBindings() {
        var detail = service.getCredential(5L, 1L);
        assertNull(detail.getApiSecret()); assertEquals(List.of(10L, 20L), detail.getAppIds());
    }
    @Test void ownerOfOnlyOneBoundAppCannotManageSharedCredential() {
        Application other = new Application(); other.setId(20L); other.setOwnerId(2L); other.setDeleted(0);
        when(apps.selectById(20L)).thenReturn(other);
        assertThrows(RuntimeException.class, () -> service.getCredential(5L, 1L));
        assertThrows(RuntimeException.class, () -> service.rotateSecret(5L, 1L));
        assertThrows(RuntimeException.class, () -> service.deleteCredential(5L, 1L));
        assertThrows(RuntimeException.class, () -> service.updateCredential(5L, dto(List.of(10L)), 1L));
        verify(bindings, never()).deleteBindings(anyLong());
    }
    @Test void administratorCanRepairCredentialWithDeletedApp() {
        when(security.isAdmin(1L)).thenReturn(true);
        when(apps.selectById(20L)).thenReturn(null);
        var updated = service.updateCredential(5L, dto(List.of(10L)), 1L);
        assertEquals(List.of(10L), updated.getAppIds());
        verify(bindings).deleteBindings(5L);
    }
    @Test void failedNewBindingDoesNotRemoveExistingOnes() {
        assertThrows(RuntimeException.class, () -> service.updateCredential(5L, dto(List.of(10L, 30L)), 1L));
        verify(bindings, never()).deleteBindings(anyLong());
        verify(credentials, never()).update(isNull(), any());
    }
    @Test void explicitNewBindingsReplaceOldSetWithoutChangingKey() {
        var updated = service.updateCredential(5L, dto(List.of(20L)), 1L);
        assertEquals(20L, updated.getAppId()); assertEquals(List.of(20L), updated.getAppIds());
        assertEquals("test_key", updated.getApiKey()); assertNull(updated.getApiSecret());
        assertEquals(9L, updated.getUsedCallCount()); assertEquals(63L, updated.getUsedDaysCount());
        verify(bindings).deleteBindings(5L); verify(bindings).insertBindings(5L, List.of(20L));
        verify(credentials, never()).updateById(any(ThirdPartyCredential.class));
    }
    @Test void omittedBindingFieldsPreserveAllBindings() {
        assertEquals(List.of(10L, 20L), service.updateCredential(5L, dto(null), 1L).getAppIds());
        verify(bindings, never()).deleteBindings(anyLong());
    }
    @Test void legacyFormReturningPrimaryAppDoesNotCollapseMultiAppBindings() {
        var dto = dto(null); dto.setAppId(10L);
        assertEquals(List.of(10L, 20L), service.updateCredential(5L, dto, 1L).getAppIds());
        verify(bindings, never()).deleteBindings(anyLong());
    }
    @Test void explicitEmptyBindingUpdateIsRejected() {
        assertThrows(RuntimeException.class, () -> service.updateCredential(5L, dto(List.of()), 1L));
        verify(bindings, never()).deleteBindings(anyLong());
    }
    @Test void deletionUsesLogicalDeleteAndRemovesBindings() {
        service.deleteCredential(5L, 1L);
        verify(credentials).deleteById(5L); verify(bindings).deleteBindings(5L);
    }
}
