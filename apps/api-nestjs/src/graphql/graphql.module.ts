import { Module } from '@nestjs/common'
import { GraphQLModule as ApolloGraphQLModule } from '@nestjs/graphql'
import * as GraphQLJSON from 'graphql-type-json'
import { DateScalar } from '@app/graphql/scalars/date.scalar'
import { BufferScalar } from '@app/graphql/scalars/buffer.scalar'
import { DecimalScalar } from '@app/graphql/scalars/decimal.scalar'
import { join } from 'path'
import { ConfigService } from '@app/shared/config.service'
import { StatisticValueScalar } from '@app/graphql/scalars/statistic-value.scalar'
import { LongScalar } from '@app/graphql/scalars/long.scalar'
import { PubSub } from 'graphql-subscriptions'

@Module({
  imports: [
    ApolloGraphQLModule.forRootAsync({
      useFactory: async (configService: ConfigService): Promise<any> => {
        const config = configService.graphql

        return {
          typePaths: ['./src/**/*.graphql'],
          resolvers: { JSON: GraphQLJSON },
          cacheControl: true,
          installSubscriptionHandlers: true,
          cors: true,
          definitions: {
            path: join(process.cwd(), 'src/graphql/schema.ts'),
            outputAs: 'class'
          },
          context: ({ req, res }) => ({
            request: req,
            response: res
          }),
          ...config
        }
      },
      inject: [ConfigService]
    })
  ],
  providers: [
    DateScalar, DecimalScalar, BufferScalar, StatisticValueScalar, LongScalar,
    {
      provide: 'PUB_SUB',
      useValue: new PubSub()
    }
  ]
})
export class GraphQLModule {}
