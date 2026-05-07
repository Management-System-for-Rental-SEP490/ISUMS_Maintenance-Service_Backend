package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.PlanDTO.CreatePlanRequest;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDetailDto;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.domains.enums.FrequencyType;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.mappers.PlanMapper;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import com.isums.userservice.grpc.UserResponse;
import common.i18n.TranslationMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodicInspectionPlanServiceImpl")
class PeriodicInspectionPlanServiceImplTest {

    @Mock private PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    @Mock private PlanMapper planMapper;
    @Mock private PlanHouseRepository planHouseRepository;
    @Mock private UserClientsGrpc userClientsGrpc;
    @Mock private TranslationAutoFillService translationAutoFillService;

    @InjectMocks private PeriodicInspectionPlanServiceImpl service;

    private UUID planId, houseId, managerId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        // createPlan resolves the caller's language via gRPC and fills the
        // name translations map. Lenient stubs so other non-create tests
        // don't trip strict stubbing.
        lenient().when(userClientsGrpc.getUser(anyString()))
                .thenReturn(UserResponse.newBuilder().setLanguage("vi").build());
        lenient().when(translationAutoFillService.complete(any(), anyString()))
                .thenReturn(new TranslationMap());
    }

    @Nested
    @DisplayName("createPlan")
    class CreatePlan {

        @Test
        @DisplayName("saves plan as active with managerId from Keycloak subject")
        void happy() {
            CreatePlanRequest req = new CreatePlanRequest(
                    "monthly", FrequencyType.MONTHLY, 1,
                    LocalDate.now(), LocalDate.now().plusYears(1), LocalDate.now().plusMonths(1));

            when(periodicInspectionPlanRepository.save(any(PeriodicInspectionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(planMapper.plan(any())).thenReturn(stubPlanDto());

            service.createPlan(managerId.toString(), req);

            ArgumentCaptor<PeriodicInspectionPlan> cap = ArgumentCaptor.forClass(PeriodicInspectionPlan.class);
            verify(periodicInspectionPlanRepository).save(cap.capture());
            assertThat(cap.getValue().getManagerId()).isEqualTo(managerId);
            assertThat(cap.getValue().getIsActive()).isTrue();
            assertThat(cap.getValue().getFrequencyType()).isEqualTo(FrequencyType.MONTHLY);
        }

        @Test
        @DisplayName("wraps when managerId not a UUID")
        void badManagerId() {
            CreatePlanRequest req = new CreatePlanRequest(
                    "x", FrequencyType.MONTHLY, 1, LocalDate.now(), null, LocalDate.now());

            assertThatThrownBy(() -> service.createPlan("not-a-uuid", req))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("addHousesToPlan")
    class AddHouses {

        @Test
        @DisplayName("deduplicates input and skips houses already in plan")
        void deduplicates() {
            UUID already = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();

            when(periodicInspectionPlanRepository.existsById(planId)).thenReturn(true);
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(
                    PlanHouse.builder().id(UUID.randomUUID()).planId(planId).houseId(already).build()
            ));
            when(planHouseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(planMapper.planHouseDtos(anyList())).thenAnswer(inv -> List.of());

            service.addHousesToPlan(planId, List.of(fresh, fresh, already));

            ArgumentCaptor<List<PlanHouse>> cap = ArgumentCaptor.forClass(List.class);
            verify(planHouseRepository).saveAll(cap.capture());
            assertThat(cap.getValue()).hasSize(1);
            assertThat(cap.getValue().getFirst().getHouseId()).isEqualTo(fresh);
        }

        @Test
        @DisplayName("wraps when plan does not exist")
        void planMissing() {
            when(periodicInspectionPlanRepository.existsById(planId)).thenReturn(false);

            assertThatThrownBy(() -> service.addHousesToPlan(planId, List.of(houseId)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getPlanById")
    class GetById {

        @Test
        @DisplayName("returns PlanDetailDto with house ids from PlanHouse table")
        void happy() {
            PeriodicInspectionPlan plan = PeriodicInspectionPlan.builder()
                    .id(planId).name("p").frequencyType(FrequencyType.MONTHLY).frequencyValue(1)
                    .effectiveFrom(LocalDate.now()).nextRunAt(LocalDate.now()).build();
            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(
                    PlanHouse.builder().planId(planId).houseId(houseId).build()
            ));

            PlanDetailDto dto = service.getPlanById(planId);

            assertThat(dto.id()).isEqualTo(planId);
            assertThat(dto.houseIds()).containsExactly(houseId);
        }

        @Test
        @DisplayName("wraps when plan missing")
        void missing() {
            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanById(planId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("removeHouseFromPlan")
    class RemoveHouse {

        @Test
        @DisplayName("deletes the plan-house mapping and returns true")
        void happy() {
            PlanHouse ph = PlanHouse.builder().id(UUID.randomUUID()).planId(planId).houseId(houseId).build();
            when(planHouseRepository.findByPlanIdAndHouseId(planId, houseId))
                    .thenReturn(Optional.of(ph));

            assertThat(service.removeHouseFromPlan(planId, houseId)).isTrue();
            verify(planHouseRepository).delete(ph);
        }

        @Test
        @DisplayName("wraps when mapping not found")
        void notFound() {
            when(planHouseRepository.findByPlanIdAndHouseId(planId, houseId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeHouseFromPlan(planId, houseId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("trivial passthroughs")
    class Trivial {

        @Test
        @DisplayName("getAllPlans returns mapped list")
        void getAll() {
            when(periodicInspectionPlanRepository.findAll()).thenReturn(List.of());
            when(planMapper.plans(anyList())).thenReturn(List.of());

            assertThat(service.getAllPlans()).isEmpty();
        }

        @Test
        @DisplayName("getAllPlanHouse returns mapped list")
        void getAllPlanHouse() {
            when(planHouseRepository.findAll()).thenReturn(List.of());
            when(planMapper.planHouseDtos(anyList())).thenReturn(List.of());

            assertThat(service.getAllPlanHouse()).isEmpty();
        }
    }

    private PlanDto stubPlanDto() {
        return new PlanDto(planId, managerId, "p", FrequencyType.MONTHLY, 1,
                LocalDate.now(), null, LocalDate.now(), true, null, null);
    }

    private PlanHouseDto stubPhDto() {
        return new PlanHouseDto(UUID.randomUUID(), planId, houseId, null);
    }
}
