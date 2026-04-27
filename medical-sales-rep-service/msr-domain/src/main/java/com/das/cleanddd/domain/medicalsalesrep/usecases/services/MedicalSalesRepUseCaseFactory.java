package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepFactory;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepNamesInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.UpdateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;

@Service
public class MedicalSalesRepUseCaseFactory {

    private final IMedicalSalesRepRepository medicalSalesRepRepository;
    private final MedicalSalesRepFactory medicalSalesRepFactory = new MedicalSalesRepFactory();
    private final MedicalSalesRepMapper medicalSalesRepMapper = new MedicalSalesRepMapper();
    
    private final CreateMedicalSalesRepUseCase createMedicalSalesRepUseCase;
    private final UpdateMedicalSalesRepUseCase updateMedicalSalesRepUseCase;
    private final ActivateMedicalSalesRepUseCase activateMedicalSalesRepUseCase;
    private final DeactivateMedicalSalesRepUseCase deactivateMedicalSalesRepUseCase;
    private final GetMedicalSalesRepByIdUseCase getMedicalSalesRepByIdUseCase;
    private final FindMedicalSalesRepByNameUseCase findMedicalSalesRepByNameUseCase;
    

    public MedicalSalesRepUseCaseFactory(IMedicalSalesRepRepository medicalSalesRepRepository) {

        this.medicalSalesRepRepository = medicalSalesRepRepository;
        this.createMedicalSalesRepUseCase = new CreateMedicalSalesRepUseCase(this.medicalSalesRepRepository, this.medicalSalesRepFactory, this.medicalSalesRepMapper);
        this.updateMedicalSalesRepUseCase = new UpdateMedicalSalesRepUseCase(this.medicalSalesRepRepository, this.medicalSalesRepFactory, this.medicalSalesRepMapper);
        this.activateMedicalSalesRepUseCase = new ActivateMedicalSalesRepUseCase(this.medicalSalesRepRepository);
        this.deactivateMedicalSalesRepUseCase = new DeactivateMedicalSalesRepUseCase(this.medicalSalesRepRepository);
        this.getMedicalSalesRepByIdUseCase = new GetMedicalSalesRepByIdUseCase(this.medicalSalesRepRepository, this.medicalSalesRepMapper);
        this.findMedicalSalesRepByNameUseCase = new FindMedicalSalesRepByNameUseCase(this.medicalSalesRepRepository, this.medicalSalesRepMapper);

    }

    public UseCase<CreateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> getCreateMedicalSalesRepUseCase() {
        return createMedicalSalesRepUseCase;
    }
    public UseCase<UpdateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> getUpdateMedicalSalesRepUseCase() {
        return updateMedicalSalesRepUseCase;
    }
    public UseCaseOnlyInput<MedicalSalesRepIDDto> getActivateMedicalSalesRepUseCase() {
        return activateMedicalSalesRepUseCase;
    }
    public UseCaseOnlyInput<MedicalSalesRepIDDto> getDeActivateMedicalSalesRepUseCase() {
        return deactivateMedicalSalesRepUseCase;
    }
    public UseCase<MedicalSalesRepIDDto, MedicalSalesRepOutputDTO> getGetMedicalSalesRepByIdUseCase() {
        return getMedicalSalesRepByIdUseCase;
    }
    public UseCase<MedicalSalesRepNamesInputDTO, List<MedicalSalesRepOutputDTO>> findMedicalSalesRepByNameUseCase() {
        return findMedicalSalesRepByNameUseCase;
    }
}
