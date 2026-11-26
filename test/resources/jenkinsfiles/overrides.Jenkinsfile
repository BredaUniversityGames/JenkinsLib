// Module overrides, used to check how parameter defaults are chosen.
@Library('JenkinsLib') _

stages {
    perforce.sync(P4_HOST: 'ssl:custom:1666')
    // README suggests UE5_BUILD_CONFIG: 'Shipping' sets the default; today only *_CHOICES overrides work.
    ue5.build(UE5_BUILD_CONFIG: 'Shipping', UE5_BUILD_PLATFORM_CHOICES: ['Linux', 'Win64'])
}
