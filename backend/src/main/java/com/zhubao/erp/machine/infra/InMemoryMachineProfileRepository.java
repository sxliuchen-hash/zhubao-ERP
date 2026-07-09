package com.zhubao.erp.machine.infra;

import com.zhubao.erp.machine.domain.MachineProfile;
import com.zhubao.erp.machine.domain.MachineProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "zhubao.machine-profile.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryMachineProfileRepository implements MachineProfileRepository {

    private final ConcurrentMap<Long, MachineProfile> profiles = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(9000);

    @Override
    public MachineProfile create(MachineProfile profile) {
        long id = sequence.incrementAndGet();
        MachineProfile saved = withId(profile, id);
        profiles.put(id, saved);
        return saved;
    }

    @Override
    public MachineProfile update(MachineProfile profile) {
        profiles.put(profile.id(), profile);
        return profile;
    }

    @Override
    public Optional<MachineProfile> findById(Long id) {
        return Optional.ofNullable(profiles.get(id));
    }

    @Override
    public Optional<MachineProfile> findByMachineNo(String machineNo) {
        return profiles.values().stream()
                .filter(profile -> profile.machineNo().equals(machineNo))
                .findFirst();
    }

    @Override
    public List<MachineProfile> findAll() {
        return profiles.values().stream()
                .sorted(Comparator.comparing(MachineProfile::machineNo))
                .toList();
    }

    // 记录不可变，创建时用生成的自增主键重建
    private MachineProfile withId(MachineProfile profile, Long id) {
        return new MachineProfile(
                id, profile.machineNo(), profile.machineName(), profile.machineType(),
                profile.location(), profile.enabled(), profile.remark(),
                profile.createdBy(), profile.createdAt()
        );
    }
}
