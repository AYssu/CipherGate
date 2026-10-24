package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.transfer.*;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferResult.Action;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsSessionKickService;
import com.ayssu.ciphergate.util.SecurityUtils;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppUserTransferServiceTest {
    ApplicationMapper apps; AppUserMapper users; AppUserBindingMapper bindings; AppUserTrialMapper trials;
    AppUserTransferMapper sql; SecurityUtils security; UserMembershipService quota; AppUserWsSessionKickService kicks;
    AppUserTransferService service; AppUser user; Application source; Application target;

    @BeforeEach void setup() {
        apps = mock(ApplicationMapper.class); users = mock(AppUserMapper.class); bindings = mock(AppUserBindingMapper.class);
        trials = mock(AppUserTrialMapper.class); sql = mock(AppUserTransferMapper.class); security = mock(SecurityUtils.class);
        quota = mock(UserMembershipService.class); kicks = mock(AppUserWsSessionKickService.class);
        service = new AppUserTransferService(apps, users, bindings, trials, sql, security, quota, kicks);
        source = app(10L, 1L); target = app(20L, 1L); user = account(100L, 10L, "alpha", "alpha@example.test");
        when(apps.selectById(10L)).thenReturn(source); when(apps.selectById(20L)).thenReturn(target);
        when(sql.lockApplication(10L)).thenReturn(source); when(sql.lockApplication(20L)).thenReturn(target);
        when(sql.lockOwner(1L)).thenReturn(1L);
        when(sql.selectedUsers(eq(10L), anyList(), anyBoolean())).thenReturn(List.of(user));
        when(sql.conflicts(eq(10L), anyString(), any(), eq(true), anyBoolean())).thenReturn(List.of(user));
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of());
        when(sql.bindings(anyLong(), anyLong(), anyBoolean())).thenReturn(List.of());
        when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(true);
        when(users.insert(any(AppUser.class))).thenAnswer(a -> { a.<AppUser>getArgument(0).setId(200L); return 1; });
        when(users.deleteById(anyLong())).thenReturn(1);
        when(sql.overwrite(any())).thenReturn(1);
        when(sql.restore(any())).thenReturn(1);
        when(sql.claimBatch(any())).thenReturn(1);
        when(sql.retainBan(anyLong(), any(), any(), any())).thenReturn(1);
    }
    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    static Application app(Long id, Long owner) {
        Application a = new Application(); a.setId(id); a.setOwnerId(owner); a.setAppName("App " + id); a.setDeleted(0); return a;
    }
    static AppUser account(Long id, Long app, String name, String email) {
        AppUser u = new AppUser(); u.setId(id); u.setAppId(app); u.setUsername(name); u.setEmail(email);
        u.setPassword("TEST_ONLY_ENCODED_HASH"); u.setDeleted(0); return u;
    }
    AppUserTransferRequest request() {
        AppUserTransferRequest r = new AppUserTransferRequest(); r.setSourceAppId(10L); r.setTargetAppId(20L);
        r.setMode(AppUserTransferRequest.Mode.COPY); r.setUserIds(List.of(100L)); return r;
    }
    void token(AppUserTransferRequest r) { r.setPreviewToken(service.preview(r, 1L).previewToken()); }
    AppUserTransferResult execute(AppUserTransferRequest r) {
        TransactionSynchronizationManager.initSynchronization(); return service.execute(r, 1L);
    }
    void commitCallbacks() { TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit()); }

    @Test void previewIsReadOnlyAndContainsNoCredentials() {
        var result = service.preview(request(), 1L);
        assertEquals(1, result.createCount()); assertFalse(result.executed());
        assertFalse(result.toString().contains(user.getPassword()));
        verify(users, never()).insert(any(AppUser.class)); verify(users, never()).deleteById(anyLong());
        verifyNoInteractions(kicks);
    }
    @Test void mustOwnBothApplicationsAndDelegatedAgentsCannotTransfer() {
        target.setOwnerId(2L);
        assertThrows(SecurityException.class, () -> service.preview(request(), 1L));
        verifyNoInteractions(quota);
        when(security.isAdmin(1L)).thenReturn(true);
        assertEquals(1, service.preview(request(), 1L).createCount());
    }
    @Test void validatesSameAppDuplicateIdsAndBatchLimit() {
        var r = request(); r.setTargetAppId(10L); assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
        r.setTargetAppId(20L); r.setUserIds(List.of(100L, 100L)); assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
        r.setUserIds(java.util.stream.LongStream.range(1, 102).boxed().toList());
        assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
    }
    @Test void foreignOrDeletedSourceIsBlocked() {
        when(sql.selectedUsers(eq(10L), anyList(), anyBoolean())).thenReturn(List.of());
        assertEquals(1, service.preview(request(), 1L).blockCount());
    }
    @Test void defaultConflictSkippedAndSkippedMoveDoesNotDeleteSource() {
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean()))
                .thenReturn(List.of(account(200L, 20L, "alpha", "different@example.test")));
        var r = request(); r.setMode(AppUserTransferRequest.Mode.MOVE); token(r);
        assertEquals(1, execute(r).skipCount());
        verify(users, never()).deleteById(anyLong()); verifyNoInteractions(kicks);
    }
    @Test void multipleTargetsBlockedEvenWithForce() {
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean()))
                .thenReturn(List.of(account(200L, 20L, "alpha", null), account(201L, 20L, "beta", "alpha@example.test")));
        var r = request(); r.setForceOverwrite(true); token(r);
        assertEquals(1, service.preview(r, 1L).blockCount());
        assertThrows(IllegalArgumentException.class, () -> execute(r));
        verify(users, never()).insert(any(AppUser.class));
    }
    @Test void deletedTargetRestoresWithoutForceAndUsesQuota() {
        AppUser old = account(300L, 20L, "ALPHA", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        var r = request(); var preview = service.preview(r, 1L);
        assertEquals(1, preview.restoreCount()); assertEquals(0, preview.blockCount());
        assertEquals(0, preview.createCount()); assertEquals(0, preview.overwriteCount());
        assertEquals(Action.RESTORE, preview.items().get(0).action());
        r.setPreviewToken(preview.previewToken()); var result = execute(r);
        assertEquals(300L, result.items().get(0).targetUserId()); assertEquals(1, result.restoreCount());
        verify(sql).restore(argThat(u -> u.getId().equals(300L) && u.getAppId().equals(20L)
                && u.getDeleted() == 0 && u.getPassword().equals(user.getPassword())));
        verify(sql, never()).overwrite(any()); verify(users, never()).insert(any(AppUser.class));
        verify(users, never()).deleteById(anyLong()); verify(quota).consumeUserRegisterQuota(1L, 1);
        verify(sql).claimBatch(argThat(batch -> batch.createCount() == 0 && batch.overwriteCount() == 1));
        verifyNoInteractions(kicks); commitCallbacks(); verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
    }
    @Test void restoredTargetAlsoWorksWithForceAndSameOwnerMoveAtQuotaLimit() {
        var old = account(300L, 20L, "alpha", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(false);
        var r = request(); r.setForceOverwrite(true); r.setMode(AppUserTransferRequest.Mode.MOVE);
        token(r); assertEquals(Action.RESTORE, execute(r).items().get(0).action());
        verifyNoInteractions(quota); verify(users).deleteById(100L);
        commitCallbacks(); verify(kicks).kickByAppUserId(eq(100L), isNull(), any());
        verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
    }
    @Test void restoringDeletedTargetCannotBypassQuota() {
        var old = account(300L, 20L, "alpha", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        var r = request(); token(r); when(quota.checkUserRegisterQuota(1L, 1)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
        assertThrows(IllegalArgumentException.class, () -> execute(r));
        verify(sql, never()).restore(any()); verify(sql, never()).claimBatch(any());
    }
    @Test void deletedAndActiveCrossNamespaceIdentityCannotBeRestoredOrOverwritten() {
        var old = account(300L, 20L, "someone-else", "alpha");
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        var r = request();
        old.setDeleted(1); assertEquals(1, service.preview(r, 1L).blockCount());
        r.setForceOverwrite(true); assertEquals(1, service.preview(r, 1L).blockCount());
        old.setDeleted(0); assertEquals(1, service.preview(r, 1L).blockCount());
    }
    @Test void multipleTargetsIncludingDeletedAreBlockedAndNeverAutoMerged() {
        var old = account(300L, 20L, "alpha", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean()))
                .thenReturn(List.of(old, account(301L, 20L, "other", "alpha@example.test")));
        var r = request(); token(r); assertEquals(1, service.preview(r, 1L).blockCount());
        assertThrows(IllegalArgumentException.class, () -> execute(r)); verify(sql, never()).restore(any());
    }
    @Test void twoSelectedUsersCannotRestoreSameTarget() {
        var old = account(300L, 20L, "alpha", "beta@example.test"); old.setDeleted(1);
        var other = account(101L, 10L, "beta", "beta@example.test");
        when(sql.selectedUsers(eq(10L), anyList(), anyBoolean())).thenReturn(List.of(user, other));
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        var r = request(); r.setUserIds(List.of(100L, 101L)); token(r);
        var preview = service.preview(r, 1L); assertEquals(1, preview.restoreCount()); assertEquals(1, preview.blockCount());
        assertThrows(IllegalArgumentException.class, () -> execute(r)); verify(sql, never()).restore(any());
    }
    @Test void targetRestoredAfterPreviewRequiresNewPreview() {
        var old = account(300L, 20L, "alpha", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        var r = request(); token(r); old.setDeleted(0);
        assertThrows(IllegalArgumentException.class, () -> execute(r)); verify(sql, never()).restore(any());
    }
    @Test void failedRestoreDoesNotDeleteSourceOrScheduleKicks() {
        var old = account(300L, 20L, "alpha", null); old.setDeleted(1);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        when(sql.restore(any())).thenReturn(0);
        var r = request(); r.setMode(AppUserTransferRequest.Mode.MOVE); token(r);
        assertThrows(IllegalStateException.class, () -> execute(r));
        verify(users, never()).deleteById(anyLong()); verifyNoInteractions(kicks);
    }
    @Test void sourceAmbiguousLoginBlocked() {
        when(sql.conflicts(eq(10L), anyString(), any(), eq(true), anyBoolean()))
                .thenReturn(List.of(user, account(101L, 10L, "alpha@example.test", null)));
        assertEquals(1, service.preview(request(), 1L).blockCount());
    }
    @Test void executeRequiresPreviewAndRejectsChanges() {
        var r = request(); assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        token(r); user.setMemberExpiresAt(LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        verify(users, never()).insert(any(AppUser.class)); verifyNoInteractions(kicks);
    }
    @Test void copyCreatesNewIdPreservingHashNotAgentOrOnlineHistory() {
        user.setAgentId(888L); user.setLoginCount(12); user.setLastLoginAt(LocalDateTime.now());
        user.setMemberExpiresAt(LocalDateTime.now().plusDays(2));
        var r = request(); token(r); var result = execute(r);
        assertEquals(200L, result.items().get(0).targetUserId());
        verify(users).insert(argThat((AppUser copy) -> copy.getAppId().equals(20L) && copy.getPassword().equals(user.getPassword())
                && copy.getAgentId() == null && copy.getLoginCount() == 0 && copy.getLastLoginAt() == null
                && copy.getMemberExpiresAt().equals(user.getMemberExpiresAt())));
        verify(users, never()).deleteById(anyLong()); verify(quota).consumeUserRegisterQuota(1L, 1);
    }
    @Test void moveDeletesSourceAndKicksOnlyAfterCommit() {
        var r = request(); r.setMode(AppUserTransferRequest.Mode.MOVE); token(r); execute(r);
        verify(users).deleteById(100L); verifyNoInteractions(kicks);
        commitCallbacks(); verify(kicks).kickByAppUserId(eq(100L), isNull(), any());
    }
    @Test void overwriteRetainsTargetIdAndDoesNotUseQuota() {
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean()))
                .thenReturn(List.of(account(300L, 20L, "alpha", null)));
        var r = request(); r.setForceOverwrite(true); token(r); var result = execute(r);
        assertEquals(Action.OVERWRITE, result.items().get(0).action()); assertEquals(300L, result.items().get(0).targetUserId());
        verify(sql).overwrite(argThat(u -> u.getId().equals(300L) && u.getAppId().equals(20L)));
        verify(users, never()).insert(any(AppUser.class)); verifyNoInteractions(quota); verifyNoInteractions(kicks);
        commitCallbacks(); verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
    }
    @Test void newAccountsEnforceTargetOwnerQuotaDuringPreviewAndExecution() {
        var r = request(); token(r); when(quota.checkUserRegisterQuota(1L, 1)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        verify(users, never()).insert(any(AppUser.class));
    }
    @Test void sameOwnerMoveWorksAtQuotaLimitWithoutChargingAnotherRegistration() {
        when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(false);
        var r = request(); r.setMode(AppUserTransferRequest.Mode.MOVE); token(r); execute(r);
        verifyNoInteractions(quota); verify(users).deleteById(100L);
    }
    @Test void crossOwnerMoveStillRequiresTargetCapacity() {
        target.setOwnerId(2L); when(security.isAdmin(1L)).thenReturn(true);
        when(quota.checkUserRegisterQuota(2L, 1)).thenReturn(false);
        var r = request(); r.setMode(AppUserTransferRequest.Mode.MOVE);
        assertThrows(IllegalArgumentException.class, () -> service.preview(r, 1L));
        verify(quota).checkUserRegisterQuota(2L, 1);
    }
    @Test void sourceBanReactivatedOnDeletedTargetBindingAndLicenseRightsNotCopied() {
        AppUserBinding banned = new AppUserBinding(); banned.setId(5L); banned.setDeviceId("dev");
        banned.setBindType("LICENSE"); banned.setLicenseKeyId(999L); banned.setIsBanned(true); banned.setDeleted(0);
        when(sql.bindings(eq(10L), eq(100L), anyBoolean())).thenReturn(List.of(banned));
        when(bindings.insert(any(AppUserBinding.class))).thenReturn(1);
        var r = request(); token(r); execute(r);
        verify(bindings).insert(argThat((AppUserBinding b) -> b.getBindType().equals("ACCOUNT") && b.getIsBanned()
                && b.getLicenseKeyId() == null && b.getExpiresAt() == null && b.getLastActiveAt() == null));
    }
    @Test void targetBanIsNeverClearedAndSourceBanReactivatesDeletedDevice() {
        AppUserBinding b = new AppUserBinding(); b.setId(5L); b.setDeviceId("dev"); b.setIsBanned(true); b.setDeleted(0);
        when(sql.bindings(eq(10L), eq(100L), anyBoolean())).thenReturn(List.of(b));
        AppUserBinding existing = new AppUserBinding(); existing.setId(6L); existing.setIsBanned(true); existing.setDeleted(1);
        when(sql.deviceBinding(20L, 200L, "dev")).thenReturn(existing);
        var r = request(); token(r); execute(r);
        verify(sql).retainBan(eq(6L), isNull(), isNull(), any());
        verify(bindings, never()).insert(any(AppUserBinding.class));
    }
    @Test void existingTrialCannotBeExtendedByOverwrite() {
        var old = account(300L, 20L, "alpha", null);
        when(sql.conflicts(eq(20L), anyString(), any(), eq(false), anyBoolean())).thenReturn(List.of(old));
        AppUserTrial sourceTrial = new AppUserTrial(); sourceTrial.setTrialExpiresAt(LocalDateTime.now().plusDays(5));
        AppUserTrial targetTrial = new AppUserTrial(); targetTrial.setId(3L); targetTrial.setTrialExpiresAt(LocalDateTime.now().minusDays(1));
        when(sql.trial(eq(10L), eq(100L), anyBoolean())).thenReturn(sourceTrial);
        when(sql.trial(eq(20L), eq(300L), anyBoolean())).thenReturn(targetTrial);
        var r = request(); r.setForceOverwrite(true); token(r); execute(r);
        verify(sql, never()).updateTrial(any()); verify(trials, never()).insert(any(AppUserTrial.class));
    }
}
