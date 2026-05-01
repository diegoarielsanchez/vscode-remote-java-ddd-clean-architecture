package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfFactory;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfNamesInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfSpecialtyInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.UpdateHealthCareProfInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;

@Service
public class HealthCareProfUseCaseFactory {

    private final IHealthCareProfRepository _repository;
    private final HealthCareProfFactory _factory = new HealthCareProfFactory();
    private final HealthCareProfMapper _mapper = new HealthCareProfMapper();
    
    private final CreateHealthCareProfUseCase createHealthCareProfUseCase;
    private final UpdateHealthCareProfUseCase updateHealthCareProfUseCase;
    private final ActivateHealthCareProfUseCase activateHealthCareProfUseCase;
    private final DeactivateHealthCareProfUseCase deactivateHealthCareProfUseCase;
    private final GetHealthCareProfByIdUseCase getHealthCareProfByIdUseCase;
    private final FindHealthCareProfByNameUseCase findHealthCareProfByNameUseCase;
    private final FindHealthCareProfBySpecialtyUseCase findHealthCareProfBySpecialtyUseCase;
    

    public HealthCareProfUseCaseFactory(IHealthCareProfRepository entityRepository, IHcpEventPublisher eventPublisher) {

        this._repository = entityRepository;
        this.createHealthCareProfUseCase = new CreateHealthCareProfUseCase(this._repository, this._factory, this._mapper, eventPublisher);
        this.updateHealthCareProfUseCase = new UpdateHealthCareProfUseCase(this._repository, this._factory, this._mapper, eventPublisher);
        this.activateHealthCareProfUseCase = new ActivateHealthCareProfUseCase(this._repository, eventPublisher);
        this.deactivateHealthCareProfUseCase = new DeactivateHealthCareProfUseCase(this._repository, eventPublisher);
        this.getHealthCareProfByIdUseCase = new GetHealthCareProfByIdUseCase(this._repository, this._mapper);
        this.findHealthCareProfByNameUseCase = new FindHealthCareProfByNameUseCase(this._repository, this._mapper);
        this.findHealthCareProfBySpecialtyUseCase = new FindHealthCareProfBySpecialtyUseCase(this._repository, this._mapper);

    }

    public UseCase<CreateHealthCareProfInputDTO, HealthCareProfOutputDTO> getCreateHealthCareProfUseCase() {
        return createHealthCareProfUseCase;
    }
    public UseCase<UpdateHealthCareProfInputDTO, HealthCareProfOutputDTO> getUpdateHealthCareProfUseCase() {
        return updateHealthCareProfUseCase;
    }
    public UseCaseOnlyInput<HealthCareProfIDDto> getActivateHealthCareProfUseCase() {
        return activateHealthCareProfUseCase;
    }
    public UseCaseOnlyInput<HealthCareProfIDDto> getDeactivateHealthCareProfUseCase() {
        return deactivateHealthCareProfUseCase;
    }
    public UseCase<HealthCareProfIDDto, HealthCareProfOutputDTO> getGetHealthCareProfByIdUseCase() {
        return getHealthCareProfByIdUseCase;
    }
    public UseCase<HealthCareProfNamesInputDTO, List<HealthCareProfOutputDTO>> getFindHealthCareProfByNameUseCase() {
        return findHealthCareProfByNameUseCase;
    }

    public UseCase<HealthCareProfSpecialtyInputDTO, List<HealthCareProfOutputDTO>> getFindHealthCareProfBySpecialtyUseCase() {
        return findHealthCareProfBySpecialtyUseCase;
    }
}
