package com.kcalma.weight;

import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.weight.dto.UpsertWeightRequest;
import com.kcalma.weight.dto.UpsertWeightResponse;
import com.kcalma.weight.dto.WeightEntryResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightEntryService {

    private final WeightEntryRepository repository;
    private final UserProfileRepository profileRepository;

    public WeightEntryService(WeightEntryRepository repository, UserProfileRepository profileRepository) {
        this.repository = repository;
        this.profileRepository = profileRepository;
    }

    /**
     * Upserts the weigh-in for one day. When {@code date} is the user's most recent weigh-in
     * (no other row has a later {@code entryDate}), {@code user_profile.weight_kg} is refreshed
     * too so the daily nutrition targets recalculate from the latest weight — an edit to an
     * older date never touches the profile weight.
     */
    @Transactional
    public UpsertWeightResponse upsert(UUID userId, LocalDate date, UpsertWeightRequest request) {
        WeightEntry entry = repository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> new WeightEntry(userId, date, request.weightKg()));
        entry.setWeightKg(request.weightKg());
        WeightEntry saved = repository.save(entry);

        LocalDate latestDate = repository
                .findFirstByUserIdOrderByEntryDateDesc(userId)
                .map(WeightEntry::getEntryDate)
                .orElse(date);
        boolean isMostRecent = !latestDate.isAfter(date);

        boolean targetsUpdated = false;
        if (isMostRecent) {
            Optional<UserProfile> profile = profileRepository.findById(userId);
            if (profile.isPresent()) {
                profile.get().setWeightKg(request.weightKg());
                targetsUpdated = true;
            }
        }

        return new UpsertWeightResponse(WeightEntryResponse.from(saved), targetsUpdated);
    }

    @Transactional
    public boolean delete(UUID userId, LocalDate date) {
        Optional<WeightEntry> entry = repository.findByUserIdAndEntryDate(userId, date);
        entry.ifPresent(repository::delete);
        return entry.isPresent();
    }

    @Transactional(readOnly = true)
    public List<WeightEntryResponse> findRange(UUID userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to).stream()
                .map(WeightEntryResponse::from)
                .toList();
    }
}
