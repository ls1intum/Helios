import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

export default {
  theme: {
    preset: definePreset(Aura, {
      semantic: {
        primary: {
          50: '#eaf3fd',
          100: '#c0daf8',
          200: '#97c1f4',
          300: '#6da8ef',
          400: '#438fea',
          500: '#176bcf',
          600: '#1561bc',
          700: '#104b92',
          800: '#0b3668',
          900: '#072140',
          950: '#030c17',
        },
        colorScheme: {
          light: {
            surface: {
              0: '#ffffff',
              50: '{neutral.50}',
              100: '{neutral.100}',
              200: '{neutral.200}',
              300: '{neutral.300}',
              400: '{neutral.400}',
              500: '{neutral.500}',
              600: '{neutral.600}',
              700: '{neutral.700}',
              800: '{neutral.800}',
              900: '{neutral.900}',
              950: '{neutral.950}',
            },
            primary: {
              color: '{primary.900}',
              contrastColor: '#ffffff',
              hoverColor: '{primary.800}',
              activeColor: '{primary.800}',
            },
            text: {
              mutedColor: '{neutral.600}',
              hoverMutedColor: '{surface.500}',
            },
            highlight: {
              background: '{primary.800}',
              focusBackground: '{primary.700}',
              color: '#ffffff',
              focusColor: '#ffffff',
            },
          },
          dark: {
            surface: {
              0: '#ffffff',
              50: '{neutral.50}',
              100: '{neutral.100}',
              200: '{neutral.200}',
              300: '{neutral.300}',
              400: '{neutral.400}',
              500: '{neutral.500}',
              600: '{neutral.600}',
              700: '{neutral.700}',
              800: '{neutral.800}',
              900: '{neutral.900}',
              950: '{neutral.950}',
            },
            primary: {
              color: '{primary.800}',
              contrastColor: '{primary.50}',
              hoverColor: '{primary.700}',
              activeColor: '{primary.400}',
            },
            text: {
              mutedColor: '{neutral.400}',
              hoverMutedColor: '{surface.300}',
            },
            highlight: {
              background: '{primary.800}',
              focusBackground: '{primary.700}',
              color: '#ffffff',
              focusColor: '#ffffff',
            },
          },
        },
      },

      components: {
        toggleswitch: {
          colorScheme: {
            light: {
              root: {
                checkedBackground: '{emerald.500}',
                checkedHoverBackground: '{emerald.500}',
              },
            },
            dark: {
              root: {
                checkedBackground: '{emerald.700}',
                checkedHoverBackground: '{emerald.700}',
              },
            },
          },
        },
        button: {
          colorScheme: {
            light: {
              root: {
                success: {
                  background: '{green.600}',
                  hoverBackground: '{green.700}',
                  activeBackground: '{green.800}',
                  borderColor: '{green.600}',
                  hoverBorderColor: '{green.700}',
                  activeBorderColor: '{green.800}',
                  color: '#ffffff',
                  hoverColor: '#ffffff',
                  activeColor: '#ffffff',
                  focusRing: {
                    color: '{green.600}',
                    shadow: 'none',
                  },
                },
                danger: {
                  background: '{red.600}',
                  hoverBackground: '{red.700}',
                  activeBackground: '{red.800}',
                  borderColor: '{red.600}',
                  hoverBorderColor: '{red.700}',
                  activeBorderColor: '{red.800}',
                  color: '#ffffff',
                  hoverColor: '#ffffff',
                  activeColor: '#ffffff',
                  focusRing: {
                    color: '{red.600}',
                    shadow: 'none',
                  },
                },
                warn: {
                  background: '{orange.500}',
                  hoverBackground: '{orange.600}',
                  activeBackground: '{orange.700}',
                  borderColor: '{orange.500}',
                  hoverBorderColor: '{orange.600}',
                  activeBorderColor: '{orange.700}',
                  color: '#ffffff',
                  hoverColor: '#ffffff',
                  activeColor: '#ffffff',
                  focusRing: {
                    color: '{orange.500}',
                    shadow: 'none',
                  },
                },
                info: {
                  background: '{primary.600}',
                  hoverBackground: '{primary.700}',
                  activeBackground: '{primary.800}',
                  borderColor: '{primary.600}',
                  hoverBorderColor: '{primary.700}',
                  activeBorderColor: '{primary.800}',
                  color: '#ffffff',
                  hoverColor: '#ffffff',
                  activeColor: '#ffffff',
                  focusRing: {
                    color: '{primary.600}',
                    shadow: 'none',
                  },
                },
              },
              text: {
                primary: {
                  hoverBackground: '{primary.50}',
                  activeBackground: '{primary.100}',
                  color: '{primary.700}',
                },
                success: {
                  hoverBackground: '{green.50}',
                  activeBackground: '{green.100}',
                  color: '{green.700}',
                },
                danger: {
                  hoverBackground: '{red.50}',
                  activeBackground: '{red.100}',
                  color: '{red.700}',
                },
                warn: {
                  hoverBackground: '{orange.50}',
                  activeBackground: '{orange.100}',
                  color: '{orange.700}',
                },
                info: {
                  hoverBackground: '{primary.50}',
                  activeBackground: '{primary.100}',
                  color: '{primary.700}',
                },
              },
            },
            dark: {
              root: {
                success: {
                  background: '{green.500}',
                  hoverBackground: '{green.400}',
                  activeBackground: '{green.300}',
                  borderColor: '{green.500}',
                  hoverBorderColor: '{green.400}',
                  activeBorderColor: '{green.300}',
                  color: '{green.950}',
                  hoverColor: '{green.950}',
                  activeColor: '{green.950}',
                  focusRing: {
                    color: '{green.500}',
                    shadow: 'none',
                  },
                },
                danger: {
                  background: '{red.500}',
                  hoverBackground: '{red.400}',
                  activeBackground: '{red.300}',
                  borderColor: '{red.500}',
                  hoverBorderColor: '{red.400}',
                  activeBorderColor: '{red.300}',
                  color: '{red.950}',
                  hoverColor: '{red.950}',
                  activeColor: '{red.950}',
                  focusRing: {
                    color: '{red.500}',
                    shadow: 'none',
                  },
                },
                warn: {
                  background: '{orange.400}',
                  hoverBackground: '{orange.300}',
                  activeBackground: '{orange.200}',
                  borderColor: '{orange.400}',
                  hoverBorderColor: '{orange.300}',
                  activeBorderColor: '{orange.200}',
                  color: '{orange.950}',
                  hoverColor: '{orange.950}',
                  activeColor: '{orange.950}',
                  focusRing: {
                    color: '{orange.400}',
                    shadow: 'none',
                  },
                },
                info: {
                  background: '{primary.500}',
                  hoverBackground: '{primary.400}',
                  activeBackground: '{primary.300}',
                  borderColor: '{primary.500}',
                  hoverBorderColor: '{primary.400}',
                  activeBorderColor: '{primary.300}',
                  color: '{primary.950}',
                  hoverColor: '{primary.950}',
                  activeColor: '{primary.950}',
                  focusRing: {
                    color: '{primary.500}',
                    shadow: 'none',
                  },
                },
              },
              text: {
                primary: {
                  hoverBackground: '{primary.900}',
                  activeBackground: '{primary.800}',
                  color: '{primary.200}',
                },
                success: {
                  hoverBackground: '{green.900}',
                  activeBackground: '{green.800}',
                  color: '{green.300}',
                },
                danger: {
                  hoverBackground: '{red.900}',
                  activeBackground: '{red.800}',
                  color: '{red.300}',
                },
                warn: {
                  hoverBackground: '{orange.900}',
                  activeBackground: '{orange.800}',
                  color: '{orange.300}',
                },
                info: {
                  hoverBackground: '{primary.900}',
                  activeBackground: '{primary.800}',
                  color: '{primary.200}',
                },
              },
            },
          },
        },
        tabs: {
          colorScheme: {
            dark: {
              tab: {
                activeColor: '{primary.activeColor}',
              },
              activeBar: {
                background: '{primary.activeColor}',
              },
            },
          },
        },
        tag: {
          colorScheme: {
            light: {
              success: {
                background: '{green.100}',
                color: '{green.600}',
              },
              danger: {
                background: '{red.100}',
                color: '{red.600}',
              },
              warn: {
                background: '{orange.50}',
                color: '{orange.600}',
              },
              info: {
                background: '{blue.50}',
                color: '{blue.600}',
              },
            },
            dark: {
              success: {
                background: '{green.900}',
                color: '{green.400}',
              },
              danger: {
                background: '{red.900}',
                color: '{red.400}',
              },
              warn: {
                background: '{orange.900}',
                color: '{orange.400}',
              },
              info: {
                background: '{blue.900}',
                color: '{blue.400}',
              },
            },
          },
        },
      },
    }),
    options: {
      darkModeSelector: '.dark-mode-enabled',
      cssLayer: {
        name: 'primeng',
        order: 'tailwind-base, primeng, tailwind-utilities',
      },
    },
  },
};
